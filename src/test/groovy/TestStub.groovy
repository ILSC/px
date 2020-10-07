import com.agile.api.*
import insight.agile.AgileHelper
import insight.agile.AgileServerInfo

def info = new AgileServerInfo(URL: 'http://ncorp-amstest.ranbaxy.com:7001/Agile', username: 'admin', password: 'sun01plm')

def helper = new AgileHelper(serverInfo: info)

def aas = helper.session.getObject(IChange.OBJECT_TYPE, 'SUN-AAS-039367')

println new SendAWInfo().processAAS(aas)