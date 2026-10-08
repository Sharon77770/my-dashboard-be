"""Project-scoped durable runs. Uses the existing helper's local/SSH/root boundary."""
import base64
import socket
import uuid

RUNNER = r'''
import json, os, signal, subprocess, sys, time, selectors
from pathlib import Path
folder=Path(sys.argv[1]); metadata=json.loads((folder/'state.json').read_text())
def save():
    temporary=folder/'state.tmp';temporary.write_text(json.dumps(metadata));os.replace(temporary,folder/'state.json')
try:
    process=subprocess.Popen(['/bin/bash','-lc',metadata['command']],cwd=metadata['root'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,start_new_session=True)
except Exception:
    metadata.update(state='FAILED',exitCode=-1);save();sys.exit(1)
metadata.update(pid=process.pid,state='RUNNING',started=int(time.time()*1000),identity=Path('/proc/%s/stat'%process.pid).read_text().rsplit(')',1)[1].split()[19]);save()
def stop(*args):
    try: os.killpg(process.pid,signal.SIGKILL)
    except ProcessLookupError: pass
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGHUP,stop)
try:
    with (folder/'output.log').open('wb') as output:
        size=0
        selector=selectors.DefaultSelector();selector.register(process.stdout,selectors.EVENT_READ)
        while True:
            if not selector.select(.2):
                if process.poll() is not None: stop()
                continue
            data=os.read(process.stdout.fileno(),16384)
            if not data: break
            if size+len(data)>1048576: output.seek(0);output.truncate();size=0;metadata['truncated']=True;save()
            output.write(data);output.flush();size+=len(data)
    code=process.wait();metadata.update(exitCode=code,state='STOPPED' if (folder/'stop').exists() else ('SUCCEEDED' if code==0 else 'FAILED'),finished=int(time.time()*1000));save()
finally: stop()
'''


def run_store(root):
    folder=Path.home()/'.local/share/personal-workspace/runs'/hashlib.sha256(str(root).encode()).hexdigest()
    folder.mkdir(parents=True,exist_ok=True,mode=0o700)
    return folder


def run_state(folder):
    value=json.loads((folder/'state.json').read_text())
    if value['state']=='RUNNING':
        try:
            fields=Path('/proc/%s/stat'%value['pid']).read_text().rsplit(')',1)[1].split()
            alive=fields[0]!='Z' and fields[19]==value.get('identity')
        except (FileNotFoundError,ProcessLookupError): alive=False
        if not alive: value['state']='EXITED'
    return value


def run_view(value):
    return {key:value.get(key) for key in ('id','name','command','kind','state','pid','exitCode','started','finished','truncated')}


def run_folder(root,args):
    identity=textarg(args,'path',32)
    if not re.fullmatch('[a-f0-9]{32}',identity): raise Failure('Invalid process id')
    folder=run_store(root)/identity
    if not (folder/'state.json').is_file(): raise Failure('Process not found',404)
    return folder


def start_run(root,args,folder=None):
    store=run_store(root)
    records=list(store.glob('*/state.json'))
    if sum(run_state(item.parent)['state']=='RUNNING' for item in records)>=8: raise Failure('Maximum 8 running project processes',429)
    if folder is None:
        if len(records)>=64: raise Failure('Remove old process records before starting more',409)
        folder=store/uuid.uuid4().hex;folder.mkdir(mode=0o700)
        value=dict(id=folder.name,root=str(root),name=textarg(args,'name',80),command=textarg(args,'content',4000),kind=args.get('mode') or 'run')
        if value['kind'] not in ('run','test','build'): raise Failure('Invalid command kind')
    else: value=run_state(folder)
    if value.get('state')=='RUNNING': raise Failure('Process is already running',409)
    (folder/'stop').unlink(missing_ok=True)
    value.update(state='STARTING',pid=None,exitCode=None,finished=None,truncated=False)
    (folder/'state.json').write_text(json.dumps(value))
    child_env={key:value for key,value in env.items() if key not in ('DASHBOARD_MCP_TOKEN','DASHBOARD_MCP_URL','CODEX_API_KEY','OPENAI_API_KEY')}
    # Detached intentionally: HTTP job cancellation must not kill the managed application.
    supervisor=subprocess.Popen([sys.executable,'-c',RUNNER,str(folder)],env=child_env,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True,close_fds=True)
    threading.Thread(target=supervisor.wait,daemon=True).start()
    for unused in range(100):
        state=run_state(folder)
        if state['state']!='STARTING': return dict(tools=dict(processes=[run_view(state)]))
        time.sleep(.02)
    raise Failure('Process startup has not completed; refresh its status',504)


def stop_run(folder):
    state=run_state(folder)
    if state['state']=='EXITED' and json.loads((folder/'state.json').read_text())['state']=='RUNNING':
        for unused in range(30):
            if json.loads((folder/'state.json').read_text())['state']!='RUNNING': return
            time.sleep(.1)
        raise Failure('Previous process output is still finishing; refresh before restarting',409)
    if state['state']=='RUNNING':
        (folder/'stop').touch()
        try: os.killpg(state['pid'],signal.SIGTERM)
        except ProcessLookupError: pass
        for unused in range(30):
            if json.loads((folder/'state.json').read_text())['state']!='RUNNING': break
            time.sleep(.1)
        if run_state(folder)['state']=='RUNNING':
            try: os.killpg(state['pid'],signal.SIGKILL)
            except ProcessLookupError: pass
        for unused in range(30):
            if json.loads((folder/'state.json').read_text())['state']!='RUNNING': return
            time.sleep(.1)
        raise Failure('Process shutdown is still finishing; refresh before restarting',409)


def project_commands(root):
    commands=[]
    def add(name,command,kind): commands.append(dict(name=name,command=command,kind=kind))
    if (root/'pom.xml').is_file():
        executable='./mvnw' if (root/'mvnw').is_file() else 'mvn'
        add('Maven run',executable+' spring-boot:run','run');add('Maven test',executable+' test','test');add('Maven build',executable+' package','build')
    if (root/'build.gradle').is_file() or (root/'build.gradle.kts').is_file():
        executable='./gradlew' if (root/'gradlew').is_file() else 'gradle'
        add('Gradle run',executable+' bootRun','run');add('Gradle test',executable+' test','test');add('Gradle build',executable+' build','build')
    if (root/'package.json').is_file():
        scripts=json.loads(read_file(file_path(root,dict(path='package.json')))['content']).get('scripts',{})
        for name,kind in [('dev','run'),('start','run'),('test','test'),('build','build'),('lint','test')]:
            if name in scripts: add('npm '+name,'npm run '+name,kind)
    if any((root/name).is_file() for name in ('pyproject.toml','pytest.ini','requirements.txt')): add('pytest','python3 -m pytest','test')
    if (root/'app.py').is_file(): add('Python app','python3 app.py','run')
    return commands


def process_action(root,action,args):
    if action=='ports': return dict(tools=dict(ports=listening_ports(root)))
    if action=='run-commands': return dict(tools=dict(commands=project_commands(root)))
    if action=='run-start': return start_run(root,args)
    if action=='run-list': return dict(tools=dict(processes=[run_view(run_state(item.parent)) for item in sorted(run_store(root).glob('*/state.json'))]))
    folder=run_folder(root,args)
    if action=='run-logs':
        logfile=folder/'output.log'
        output=clean(logfile.read_bytes()[-200000:].decode('utf-8',errors='replace')) if logfile.exists() else ''
        return dict(tools=dict(processes=[run_view(run_state(folder))],output=output))
    if action in ('run-stop','run-restart'): stop_run(folder)
    if action=='run-restart': return start_run(root,args,folder)
    if action=='run-delete':
        if run_state(folder)['state'] in ('RUNNING','STARTING'): raise Failure('Stop the process first',409)
        import shutil
        shutil.rmtree(folder)
    return dict(ok=True)


def listening_ports(root):
    """Read target Linux TCP listeners and verify HTTP; never label unknown TCP as HTTP."""
    inodes={}
    for name in ('tcp','tcp6'):
        for line in Path('/proc/net/'+name).read_text().splitlines()[1:]:
            fields=line.split()
            if fields[3]=='0A': inodes[fields[9]]=int(fields[1].split(':')[1],16)
    ports={port:dict(port=port,protocol='TCP',pids=[],project=False,url=None) for port in sorted(set(inodes.values()))[:64]}
    for process in Path('/proc').iterdir():
        if not process.name.isdigit(): continue
        try:
            directory=(process/'cwd').resolve(strict=True)
            belongs=directory==root or root in directory.parents
            for descriptor in (process/'fd').iterdir():
                target=os.readlink(descriptor)
                if target.startswith('socket:['):
                    port=inodes.get(target[8:-1]);view=ports.get(port)
                    if view is not None:
                        if int(process.name) not in view['pids']:view['pids'].append(int(process.name))
                        view['project']|=belongs
        except (PermissionError,FileNotFoundError,ProcessLookupError): pass
    for port,view in ports.items():
        try:
            with socket.create_connection(('127.0.0.1',port),timeout=.15) as connection:
                connection.sendall(b'HEAD / HTTP/1.0\r\nHost: localhost\r\n\r\n')
                if connection.recv(16).startswith(b'HTTP/'):
                    view.update(protocol='HTTP',url='http://127.0.0.1:'+str(port))
        except (OSError,TimeoutError): pass
    return list(ports.values())
