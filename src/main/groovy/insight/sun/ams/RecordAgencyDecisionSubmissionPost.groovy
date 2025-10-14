package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.px.IObjectEventInfo
import com.agile.px.ISignOffEventInfo

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ExceptionConstants.*
import static insight.sun.ams.RecordAgencyDecisionRegistrationPost.changeStatus
import static insight.sun.ams.RecordAgencyDecisionRegistrationPost.updateLCPhase

class RecordAgencyDecisionSubmissionPost {
    private static final int ATT_AGENCY_RES = 1556, ATT_CATEGORY = 1060, ATT_REL_TYPE = 1546, ATT_ATTACH_TYPE = 4681
    private static final Logger logger = Logger.getLogger('insight.sun.ams.RegistrationToCommercialPost')

    void invokeScript(IBaseScriptObj obj) {
        try {
            IObjectEventInfo info = obj.PXEventInfo as ISignOffEventInfo
            IChange aas = info.dataObject as IChange
            if (aas.status.name != 'Registration Awaited') return
            String response = aas.getValue(ATT_AGENCY_RES).toString()

            switch (response) {
                case 'Approved-Release As-Is':
                    changeStatus(aas, 'Post Approval CF Review')
                    updateLCPhase(aas, 'Commercial')
                    break
                case 'Approved-Release with Changes':
                    changeStatus(aas, 'Post Approval Artwork Upload')
                    updateLCPhase(aas, 'Commercial')
                    break
                case 'Approved-Not Required in Commercial':
                    changeStatus(aas, 'Closed')
                    break
                case 'Rejected-Modify as Recommended':
                    changeStatus(aas, 'CF Review')
                    break
            }
        } catch (Exception ex) {
            obj.logFatal([ex.message, ex.cause?.message].join(' '))
            logger.log(Level.SEVERE, 'Failed to change status of AAS', ex)
            throw (ex)
        }
    }
}
