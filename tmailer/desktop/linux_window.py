"""Linux Qt desktop shell, using distro-managed QtWebEngine."""
from pathlib import Path
import sys
import threading
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
import server
if '--smoke-test' in sys.argv:
    assert (ROOT/'dist/index.html').is_file()
    print('Tmailer Linux bundle OK')
    raise SystemExit(0)
if '--no-browser' in sys.argv:
    sys.argv=[sys.argv[0],'--no-browser'];server.main()
else:
    from PyQt6.QtCore import QUrl
    from PyQt6.QtGui import QIcon,QDesktopServices
    from PyQt6.QtWidgets import QApplication,QMainWindow
    from PyQt6.QtWebEngineCore import QWebEngineProfile,QWebEnginePage
    from PyQt6.QtWebEngineWidgets import QWebEngineView
    app=QApplication(sys.argv);app.setApplicationName('Tmailer');app.setDesktopFileName('tmailer')
    app.setWindowIcon(QIcon(str(ROOT/'dist/logo.svg')))
    http=server.MailServer(('127.0.0.1',0));threading.Thread(target=http.serve_forever,daemon=True).start()
    window=QMainWindow();window.setWindowTitle('Tmailer');window.resize(1320,860)
    view=QWebEngineView();window.setCentralWidget(view)
    profile=QWebEngineProfile(window)
    class MailPage(QWebEnginePage):
        def acceptNavigationRequest(self,url,kind,is_main):
            if url.scheme() in ('http','https') and url.host()!='127.0.0.1':
                QDesktopServices.openUrl(url);return False
            return True
        def createWindow(self,kind):return self
    page=MailPage(profile,view);view.setPage(page)
    def download(item):
        from PyQt6.QtWidgets import QFileDialog
        path,_=QFileDialog.getSaveFileName(window,'Save attachment',item.downloadFileName())
        if path:
            target=Path(path);item.setDownloadDirectory(str(target.parent));item.setDownloadFileName(target.name);item.accept()
        else:item.cancel()
    profile.downloadRequested.connect(download)
    view.setUrl(QUrl(f'http://127.0.0.1:{http.server_port}/'))
    window.show()
    try:app.exec()
    finally:http.sessions.clear();http.shutdown();http.server_close()
