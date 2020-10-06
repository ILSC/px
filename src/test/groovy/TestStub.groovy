import com.agile.api.APIException
import com.agile.api.ChangeConstants
import com.agile.api.ICell
import com.agile.api.IChange
import com.agile.api.IItem
import insight.agile.AgileHelper
import insight.agile.AgileServerInfo

import static com.agile.api.ExceptionConstants.APDM_MISSINGFIELDS_WARNING
import static com.agile.api.ExceptionConstants.API_SEE_ROOT_CAUSE

def info = new AgileServerInfo(URL: 'http://apps.ilsc.com:7001/Agile', username: 'admin', password: 'tartan')

def helper = new AgileHelper(serverInfo: info)

IChange pr = helper.session.getObject(IChange.OBJECT_TYPE, 'AAS00012')

def errors = []
pr.audit(false).values().flatten().each { APIException e ->
    if (e.errorCode == APDM_MISSINGFIELDS_WARNING) {
        if (!e.message.contains('You have insufficient privileges to resolve this audit issue.'))
            errors << e.message
    } else if (e.errorCode == API_SEE_ROOT_CAUSE) {
        if (e.rootCause)
            errors << e.rootCause.message
    } else {
        errors << e.message
    }
}

println errors