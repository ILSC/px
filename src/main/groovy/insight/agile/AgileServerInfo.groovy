package insight.agile

class AgileServerInfo {
    boolean sslEnabled = false
    String host, path, URL, username, password
    int port

    String getProtocol() {
        sslEnabled ? 'https' : 'http'
    }

    String getURL() {
        this.@URL ?: buildURL()
    }

    void setPath(String path) {
        this.@path = path.startsWith('/') ? path : ('/' + path)
    }

    void setURL(String strURL) {
        def url = strURL.toURL()

        sslEnabled = url.protocol?.equalsIgnoreCase('https')
        host = url.host
        port = url.port
        path = url.path

        URL = strURL
    }

    String buildURL() {
        new URL(protocol, host, port, path).toString()
    }

    boolean isValid() {
        try {
            def url = getURL().toURL()
            url && username != null && password != null
        } catch (MalformedURLException e) {
            return false
        }
    }
}