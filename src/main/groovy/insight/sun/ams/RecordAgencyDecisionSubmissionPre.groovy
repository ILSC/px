package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.px.EventConstants
import com.agile.px.IObjectEventInfo
import com.agile.px.ISignOffEventInfo

import java.util.logging.Logger

class RecordAgencyDecisionSubmissionPre {
    private static final Logger logger = Logger.getLogger('insight.sun.ams.RegistrationToCommercialPre')
    private static final int ATT_AGENCY_RES = 1556

    void invokeScript(IBaseScriptObj obj) {
        IObjectEventInfo info = obj.PXEventInfo as ISignOffEventInfo
        IChange aas = info.dataObject as IChange
        if (aas.status.name != 'Waiting Agency Approval') return

        String response = aas.getValue(ATT_AGENCY_RES).toString()
        List appStatusList = ['Approved-Release As-Is', 'Approved-Release with Changes', 'Approved-Not Required in Commercial'],
             rejStatusList = ['Rejected-Modify as Recommended']

        if (obj.eventType == EventConstants.EVENT_APPROVE_FOR_WORKFLOW &&
                !(response in appStatusList))
            throw new Exception("Agency Response attribute must be set to ${appStatusList.join(' or ')}")

        if (obj.eventType == EventConstants.EVENT_REJECT_FOR_WORKFLOW &&
                !(response in rejStatusList))
            throw new Exception("Agency Response attribute must be set to ${rejStatusList.join(' or ')}")
    }
}