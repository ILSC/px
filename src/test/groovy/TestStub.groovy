import com.agile.api.*
import com.agile.px.ISignOffEventInfo
import insight.agile.AgileHelper
import insight.agile.AgileServerInfo
import insight.common.logging.JLogger

import java.util.logging.Logger

def info = new AgileServerInfo(URL: 'http://ncorp-amstest.ranbaxy.com:7001/Agile', username: 'admin', password: 'sun01plm')

def helper = new AgileHelper(serverInfo: info)

def aas = helper.session.getObject(IChange.OBJECT_TYPE, 'SUN-AAS-039407')
Logger logger = Logger.getLogger('insight.sun.ams.AuditStatus')

new AuditStatus().auditAAS(aas, false, logger)