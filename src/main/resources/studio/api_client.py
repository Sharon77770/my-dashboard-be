"""Bounded HTTP client executed on the selected project device, never through a shell."""
import base64
import http.client
import ssl
import uuid


def api_request(args):
    request=json.loads(textarg(args,'content',1048576))
    uri=urllib.parse.urlsplit(request['url'])
    if uri.scheme not in ('http','https') or not uri.hostname or uri.username or uri.password: raise Failure('HTTP(S) URL without credentials required')
    method=request.get('method','GET').upper()
    if method not in ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'): raise Failure('Unsupported HTTP method')
    query=urllib.parse.parse_qsl(uri.query,keep_blank_values=True)+[(item['name'],item.get('value','')) for item in (request.get('params') or []) if item.get('name')]
    headers={item['name']:item.get('value','') for item in (request.get('headers') or []) if item.get('name')}
    for key,value in headers.items():
        if not re.fullmatch(r"[!#$%&'*+.^_`|~0-9A-Za-z-]+",key) or '\r' in value or '\n' in value: raise Failure('Invalid HTTP header')
    if any(key.lower() in ('host','content-length','transfer-encoding','connection') for key in headers): raise Failure('Transport headers are managed by the client')
    body=request.get('body') or '';kind=request.get('bodyType','none');fields=request.get('fields') or []
    def content_type(value):
        if not any(key.lower()=='content-type' for key in headers): headers['Content-Type']=value
    if kind=='json':
        try: json.loads(body)
        except ValueError: raise Failure('Invalid JSON body')
        content_type('application/json')
    elif kind=='text': content_type('text/plain; charset=utf-8')
    elif kind=='form': body=urllib.parse.urlencode([(item['name'],item.get('value','')) for item in fields]);content_type('application/x-www-form-urlencoded')
    elif kind=='multipart':
        boundary='studio-'+uuid.uuid4().hex;parts=[]
        for item in fields:
            name=item['name'];filename=item.get('fileName')
            if any(char in name+(filename or '') for char in '\r\n"\\'): raise Failure('Invalid multipart field name')
            head='--'+boundary+'\r\nContent-Disposition: form-data; name="'+name+'"'
            if filename: head+='; filename="'+filename+'"'
            head+='\r\n\r\n';parts.append(head.encode())
            try: parts.append(base64.b64decode(item.get('value',''),validate=True) if filename else item.get('value','').encode())
            except ValueError: raise Failure('Invalid file encoding')
            parts.append(b'\r\n')
        parts.append(('--'+boundary+'--\r\n').encode());body=b''.join(parts)
        headers={key:value for key,value in headers.items() if key.lower()!='content-type'};headers['Content-Type']='multipart/form-data; boundary='+boundary
    elif kind=='none': body=None
    else: raise Failure('Unsupported body type')
    auth=request.get('auth') or {};auth_type=auth.get('type','none')
    if auth_type=='basic': headers['Authorization']='Basic '+base64.b64encode(((auth.get('username') or '')+':'+(auth.get('value') or '')).encode()).decode()
    elif auth_type=='bearer': headers['Authorization']='Bearer '+(auth.get('value') or '')
    elif auth_type=='api-key':
        if auth.get('location')=='query': query.append((auth.get('name',''),auth.get('value','')))
        else: headers[auth.get('name','X-API-Key')]=auth.get('value','')
    elif auth_type!='none': raise Failure('Unsupported authentication')
    for key,value in headers.items():
        if not re.fullmatch(r"[!#$%&'*+.^_`|~0-9A-Za-z-]+",key) or '\r' in value or '\n' in value: raise Failure('Invalid authentication header')
    if isinstance(body,str): body=body.encode()
    if body and len(body)>1048576: raise Failure('Request body exceeds 1 MiB',413)
    connection=None;deadline=None;started=time.monotonic()
    try:
        connection=(http.client.HTTPSConnection(uri.hostname,uri.port or 443,timeout=15,context=ssl.create_default_context()) if uri.scheme=='https' else http.client.HTTPConnection(uri.hostname,uri.port or 80,timeout=15))
        def expire():
            try:
                if connection.sock: connection.sock.shutdown(2)
            except OSError: pass
        deadline=threading.Timer(20,expire);deadline.daemon=True;deadline.start()
        path=uri.path or '/'
        if query:path+='?'+urllib.parse.urlencode(query)
        connection.request(method,path,body,headers);response=connection.getresponse();data=response.read(1048577)
        binary=False
        try: text=data[:1048576].decode('utf-8')
        except UnicodeDecodeError: text=base64.b64encode(data[:1048576]).decode();binary=True
        return dict(api=dict(status=response.status,headers=[dict(name=key,value=value) for key,value in response.getheaders()],body=text,base64=binary,latency=round((time.monotonic()-started)*1000),size=min(len(data),1048576),truncated=len(data)>1048576))
    except (OSError,http.client.HTTPException,ValueError): raise Failure('HTTP request failed: check target URL, TLS certificate and server availability',502)
    finally:
        if deadline:deadline.cancel()
        if connection:connection.close()
