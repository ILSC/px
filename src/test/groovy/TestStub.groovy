import com.agile.api.APIException
import com.agile.api.ExceptionConstants
import com.agile.api.IAgileSession
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import insight.common.logging.JLogger

import java.util.logging.Logger

import static com.agile.api.AgileSessionFactory.*
import static com.agile.api.ExceptionConstants.*

System.setProperty('insight.application.config', '/Users/nsb/Projects/Sun/ams/px/src/main/resources')
System.setProperty('logger', '/Users/nsb/Projects/Sun/ams/px/logs')

String url = 'http://apps.ilsc.com:7001/Agile', user = 'admin', password = 'tartan'

IAgileSession session = getInstance(url).createSession([(USERNAME): user, (PASSWORD): password])

IItem item = session.getObject(IItem.OBJECT_TYPE, 'AW00001')

def vals = item.getCell(1566).value.selections
println vals

session.close()