package insight.sun.ams;

import com.agile.api.*;
import com.agile.px.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.agile.api.ChangeConstants.*;
import static com.agile.api.ExceptionConstants.*;

public class RecordAgencyDecisionRegistrationPost  implements IEventAction , ICustomAction{
    private static final int ATT_AGENCY_RES = 1556;
    private static final int ATT_CATEGORY = 1060;
    private static final int ATT_REL_TYPE = 1546;
    private static final int ATT_ATTACH_TYPE = 4681;
    
    private static final Logger logger = Logger.getLogger(RecordAgencyDecisionRegistrationPost.class.getName());
    
    private static final List<Integer> warnings = Arrays.asList(
            APDM_ITEMHAS_PENDINGCHANGES_WARNING,
            APDM_DELETEALL_LOSEATTACHMENTS_WARNING,
            APDM_PENDINGCHANGE_ITEM_WARNING,
            APDM_HASPENDINGCHANGES_WARNING,
            APDM_NOTALLAPPROVERSRESPOND_WARNING
    );

    @Override
    public ActionResult doAction(IAgileSession iAgileSession, INode iNode, IDataObject iDataObject) {
        return null;
    }

    @Override
    public EventActionResult doAction(IAgileSession iAgileSession, INode iNode, IEventInfo iEventInfo) {
        try {
            return doAction((ISignOffEventInfo) iEventInfo);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to perform action." + e.getMessage(), e);
            return new EventActionResult(iEventInfo, new ActionResult(ActionResult.EXCEPTION, e));
        }
    }

    public EventActionResult doAction(ISignOffEventInfo info) throws Exception {
        IChange aas = (IChange) info.getDataObject();

        if (!info.getStatus().getName().equals("Registration Awaited")) {
            return new EventActionResult(info, new ActionResult(ActionResult.STRING, "Status is "+ aas.getStatus().getName() +", it should be 'Registration Awaited'"));
        }

        String response = aas.getValue(ATT_AGENCY_RES).toString();
        String message = "";

        switch (response) {
            case "Approved-Release As-Is":
                changeStatus(aas, "Plant Packaging Review");
                updateLCPhase(aas, "Commercial");
                break;

            case "Approved-Release with Changes":
                changeStatus(aas, "Closed");
                updateLCPhase(aas, "Registration");
                createNewAAS(aas, "Commercial", "Existing");
                break;

            case "Approved-Not Required in Commercial":
                changeStatus(aas, "Closed");
                updateLCPhase(aas, "Registration");
                break;

            case "Resubmit-Sample Required":
                changeStatus(aas, "Rejected By Agency");
                IChange newAASReg = createNewAAS(aas, "Registration", null);
                message = "New Registration AAS " + newAASReg.getName() + " created and added to Relationship tab.";
                break;

            case "Resubmit-Sample Not Required":
                changeStatus(aas, "Rejected By Agency");
                IChange newAASSub = createNewAAS(aas, "Submission", null);
                message = "New Submission AAS " + newAASSub.getName() + " created and added to Relationship tab.";
                break;
        }

        return new EventActionResult(info, new ActionResult(ActionResult.STRING, message));
    }

    private static void updateLCPhase(IChange aas, String lcPhase) throws Exception {
        ITable table = aas.getTable(TABLE_AFFECTEDITEMS);
        ITwoWayIterator iterator = table.getTableIterator();
        
        while (iterator.hasNext()) {
            IRow row = (IRow) iterator.next();
            ICell lcCell = row.getCell(ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE);
            
            if (!lcCell.getValue().toString().equals(lcPhase)) {
                IAgileList list = (IAgileList) lcCell.getAvailableValues();
                list.setSelection(new Object[]{lcPhase});
                lcCell.setValue(list);
            }
        }
    }

    private static void changeStatus(IChange aas, String toStatus) throws Exception {
        if (toStatus != null && !toStatus.isEmpty()) {
            for (Integer warning : warnings) {
                aas.getSession().disableWarning(warning);
            }
            
            IStatus status = null;
            IStatus[] nextStatuses = aas.getNextStatuses();
            for (IStatus s : nextStatuses) {
                if (s.getName().equals(toStatus)) {
                    status = s;
                    break;
                }
            }
            
            if (status != null) {
                aas.changeStatus(status, false, "", false, false, null, null, null, null, false);
            }
        }
    }

    private static IChange createNewAAS(IChange aas, String category, String relType) throws Exception {
        Map<Integer, Object> params = new HashMap<>();
        
        IAutoNumber autoNumber = aas.getAgileClass().getAutoNumberSources()[0];
        params.put(ATT_COVER_PAGE_NUMBER, autoNumber.getNextNumber());

        IAgileList catList = (IAgileList) aas.getCell(ATT_CATEGORY).getAvailableValues();
        catList.setSelection(new Object[]{category});
        params.put(ATT_CATEGORY, catList);

        if (relType != null) {
            IAgileList relTypeList = (IAgileList) aas.getCell(ATT_REL_TYPE).getAvailableValues();
            relTypeList.setSelection(new Object[]{relType});
            params.put(ATT_REL_TYPE, relTypeList);
        }

        IChange newAAS = (IChange) aas.saveAs(aas.getAgileClass(), params);
        aas.getRelationship().createRow(newAAS);
        
        IWorkflow[] workflows = newAAS.getWorkflows();
        if (workflows != null && workflows.length > 0) {
            newAAS.setWorkflow(workflows[0]);
        }
        
        updateLCPhase(newAAS, category);
        
        if (category.equals("Commercial") || category.equals("Registration")) {
            ITable attachments = aas.getAttachments();
            ITwoWayIterator iterator = attachments.getTableIterator();
            
            while (iterator.hasNext()) {
                IRow row = (IRow) iterator.next();
                IRow newRow = newAAS.getAttachments().createRow(row);
                newRow.setValue(ATT_ATTACH_TYPE, row.getValue(ATT_ATTACH_TYPE));
            }
        }
        
        IStatus defaultNextStatus = newAAS.getDefaultNextStatus();
        if (defaultNextStatus != null) {
            changeStatus(newAAS, defaultNextStatus.getName());
        }
        
        return newAAS;
    }
}
