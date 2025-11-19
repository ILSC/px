package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IAttachmentRow
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.IObjectEventInfo

import java.util.logging.Logger

import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE

class AWSignoffReport {

    private static final Logger logger = Logger.getLogger(AWSignoffReport.class.name)

    void invokeScript(IBaseScriptObj obj) {
        try {
            IObjectEventInfo info = obj.PXEventInfo as IObjectEventInfo
            IItem aw = info.dataObject
            IAttachmentRow attRow = aw.attachments.where("[$ATT_ATTACHMENTS_ATTACHMENT_TYPE] == 'Draft File'").find { true }
            attRow.file
        } catch (Exception ex) {

        }
    }


}
