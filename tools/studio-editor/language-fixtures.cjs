/* Real project toolchains reached through the same authenticated Studio local/SSH jobs. */
const assert=require('node:assert/strict');
module.exports=async({job,save,until,getProject,setProject})=>{
  const original={...getProject()};
  const javaSource='public class Message { public static String value() { return "broken"; } }';
  const javaTest='import org.junit.Test; import static org.junit.Assert.*; public class MessageTest { @Test public void message() { assertEquals("fixed", Message.value()); } }';
  const fixtures=[
    {name:'maven',files:{'pom.xml':'<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>studio</artifactId><version>1</version><properties><maven.compiler.source>8</maven.compiler.source><maven.compiler.target>8</maven.compiler.target></properties><dependencies><dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency></dependencies></project>','src/main/java/Message.java':javaSource,'src/test/java/MessageTest.java':javaTest},source:'src/main/java/Message.java',command:'mvn test',build:'mvn package'},
    {name:'gradle',files:{'build.gradle':"apply plugin: 'java'\nrepositories { mavenCentral() }\ndependencies { testImplementation 'junit:junit:4.13.2' }\n",'src/main/java/Message.java':javaSource,'src/test/java/MessageTest.java':javaTest},source:'src/main/java/Message.java',command:'gradle test',build:'gradle build'},
    {name:'npm',files:{'package.json':JSON.stringify({name:'studio-fixture',version:'1.0.0',scripts:{test:'node --test test.cjs',build:'node --check app.cjs'}}),'app.cjs':"module.exports=()=> 'broken';",'test.cjs':"require('node:test')('message',()=>require('node:assert/strict').equal(require('./app.cjs')(),'fixed'));"},source:'app.cjs',command:'npm run test',build:'npm run build'},
    {name:'pytest',files:{'pytest.ini':'[pytest]\n','app.py':"def message(): return 'broken'\n",'test_app.py':"from app import message\ndef test_message(): assert message() == 'fixed'\n"},source:'app.py',command:'python3 -m pytest',build:'python3 -m compileall -q app.py'}
  ];
  try{
    for(const fixture of fixtures){
      setProject(original);await job('mkdir',{path:fixture.name});setProject({...original,root:original.root+'/'+fixture.name});
      const directories=new Set();
      for(const [path,content] of Object.entries(fixture.files)){
        const segments=path.split('/');segments.pop();let directory='';
        for(const segment of segments){directory+=(directory?'/':'')+segment;if(!directories.has(directory)){await job('mkdir',{path:directory});directories.add(directory);}}
        await save(path,content);
      }
      assert((await job('run-commands')).tools.commands.some(item=>item.command===fixture.command));
      const run=(await job('run-start',{name:fixture.name+' tests',content:fixture.command,mode:'test'})).tools.processes[0];
      const failed=await until(()=>job('run-logs',{path:run.id}),value=>!['STARTING','RUNNING'].includes(value.tools.processes[0].state),600);
      assert.equal(failed.tools.processes[0].state,'FAILED');assert.match(failed.tools.output,/Assertion|expected|FAILED|Failures|failed/i);
      const current=await job('read',{path:fixture.source});await job('save',{path:fixture.source,revision:current.revision,content:current.content.replace('broken','fixed')});
      await job('run-restart',{path:run.id});const passed=await until(()=>job('run-logs',{path:run.id}),value=>!['STARTING','RUNNING'].includes(value.tools.processes[0].state),600);
      assert.equal(passed.tools.processes[0].state,'SUCCEEDED',fixture.name+': '+passed.tools.output.slice(-1200));
      const build=(await job('run-start',{name:fixture.name+' build',content:fixture.build,mode:'build'})).tools.processes[0];
      const built=await until(()=>job('run-logs',{path:build.id}),value=>!['STARTING','RUNNING'].includes(value.tools.processes[0].state),600);
      assert.equal(built.tools.processes[0].state,'SUCCEEDED',fixture.name+': '+built.tools.output.slice(-1200));
      console.log('PASS actual '+fixture.name+' recommendation, failing test, source save, rerun and build');
    }
  }finally{setProject(original);}
};
