"""Native-window Tmailer launcher, bundled with PyInstaller."""
import sys
from pathlib import Path
import threading
ROOT=Path(getattr(sys,'_MEIPASS',Path(__file__).resolve().parents[1]))
sys.path.insert(0,str(ROOT/'backend'))
import server

if '--smoke-test' in sys.argv:
    assert (ROOT/'dist'/'index.html').is_file()
    import mailcore
    if sys.stdout: print('Tmailer desktop bundle OK')
    raise SystemExit(0)

if '--no-browser' in sys.argv:
    sys.argv=[sys.argv[0],'--no-browser']
    server.main()
else:
    import webview
    webview.settings['ALLOW_DOWNLOADS']=True
    srv=server.MailServer(('127.0.0.1',0))
    threading.Thread(target=srv.serve_forever,daemon=True).start()
    window=webview.create_window('Tmailer',f'http://127.0.0.1:{srv.server_port}/',width=1320,height=860,min_size=(800,600),background_color='#f7f8f4')
    try:webview.start()
    finally:srv.sessions.clear();srv.shutdown();srv.server_close()
