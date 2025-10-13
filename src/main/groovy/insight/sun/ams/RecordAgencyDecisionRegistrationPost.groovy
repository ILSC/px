import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.ICell
import com.agile.api.IChange
import com.agile.api.IRow
import com.agile.api.IStatus
import com.agile.px.EventConstants
import com.agile.px.IObjectEventInfo
import com.agile.px.ISignOffEventInfo

import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.ATT_COVER_PAGE_NUMBER
import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.ChangeConstants.ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE
import static com.agile.api.ExceptionConstants.APDM_NOTALLAPPROVERSRESPOND_WARNING

class RecordAgencyDecisionRegistrationPost {
    private static final int ATT_AGENCY_RES = 1556, ATT_CATEGORY = 1060, ATT_REL_TYPE = 1546
    private static final Logger logger = Logger.getLogger('insight.sun.ams.RegistrationToCommercialPost')

    void invokeScript(IBaseScriptObj obj) {
        try {
            IObjectEventInfo info = obj.PXEventInfo as ISignOffEventInfo
            IChange aas = info.dataObject as IChange
            if (aas.status.name != 'Registration Awaited') return
            String response = aas.getValue(ATT_AGENCY_RES).toString()

            switch (response) {
                case 'Approved-Release As-Is':
                    changeStatus(aas, 'Plant Packaging Review')
                    updateLCPhase(aas, 'Commercial')
                    break
                case 'Approved-Release with Changes':
                    changeStatus(aas, 'Closed')
                    updateLCPhase(aas, 'Registration')
                    createNewAAS(aas, 'Commercial', 'Existing')
                    break
                case 'Approved-Not Required in Commercial':
                    changeStatus(aas, 'Closed')
                    updateLCPhase(aas, 'Registration')
                    break
                case 'Resubmit-Sample Required':
                    changeStatus(aas, 'Rejected By Agency')
                    createNewAAS(aas, 'Registration')
                    break
                case 'Resubmit-Sample Not Required':
                    changeStatus(aas, 'Rejected By Agency')
                    createNewAAS(aas, 'Submission')
                    break
            }
        } catch (Exception ex) {
            obj.logFatal([ex.message, ex.cause?.message].join(' '))
            logger.log(Level.SEVERE, 'Failed to change status of AAS', ex)
            throw (ex)
        }
    }

    static void updateLCPhase(IChange aas, String lcPhase) {
        aas.getTable(TABLE_AFFECTEDITEMS).each { IRow r ->
            ICell lcCell = r.getCell(ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE)
            if (lcCell.value.toString() != lcPhase) {
                def list = lcCell.availableValues
                list.selection = [lcPhase] as Object[]
                lcCell.value = list
            }
        }
    }

    static void changeStatus(IChange aas, String toStatus) {
        if (toStatus) {
            aas.session.disableWarning(APDM_NOTALLAPPROVERSRESPOND_WARNING)
            IStatus status = aas.nextStatuses.find { it.name == toStatus }
            aas.changeStatus(status, false, '', false, false, null, null,
                    null, null, false)
        }
    }

    static void createNewAAS(IChange aas, String category, String relType = null) {
        Map params = [(ATT_COVER_PAGE_NUMBER): aas.agileClass.autoNumberSources.first().nextNumber]

        def catList = aas.getCell(ATT_CATEGORY).availableValues
        catList.selection = [category] as Object[]
        params << [(ATT_CATEGORY): catList]

        if (relType) {
            def relTypeList = aas.getCell(ATT_REL_TYPE).availableValues
            relTypeList.selection = [relType] as Object[]
            params << [(ATT_REL_TYPE): relTypeList]
        }

        aas.saveAs(aas.agileClass, params)
    }
}
