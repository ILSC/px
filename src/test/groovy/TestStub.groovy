import com.agile.api.*
import com.agile.px.ISignOffEventInfo
import insight.agile.AgileHelper
import insight.agile.AgileServerInfo
import insight.common.logging.JLogger

import java.util.logging.Logger

//
def info = new AgileServerInfo(URL: 'http://ncorp-amstest.ranbaxy.com:7001/Agile', username: 'admin', password: 'sun01plm')

def helper = new AgileHelper(serverInfo: info)

//IChange aas = helper.session.getObject(IChange.OBJECT_TYPE, 'SUN-AAS-039499')
IItem aw = helper.session.getObject(IItem.OBJECT_TYPE, 'AW-021403')
////IItem aw1 = helper.session.getObject(IItem.OBJECT_TYPE, 'AW-021401')
//Logger logger = Logger.getLogger('insight.sun.ams.AuditStatus')
//
//new AuditStatus().auditAAS(aas, true, logger)
////new PropagateAttributes().copyValues(aas, aw, logger)
////new PropagateAttributes().copyValues(aas, aw1, logger)
//['1', '11', 'A1', 'D', 'AD', 'AZ', 'AZZ', 'Z', 'ZZ'].each { oldRev ->
//    String newRev = null
//    if (oldRev.matches(/^\d*$/))
//        newRev = (oldRev.toInteger() + 1).toString()
//    else if (oldRev.matches(/^.*\d$/)) {
//        newRev = oldRev[0..(oldRev.length()-2)] + (oldRev[-1..-1].toInteger() + 1)
//    } else if (oldRev.matches(/^[A-Z]*$/)){
//        char[] chrAry = oldRev.chars
//        (oldRev.length()..1).any { idx ->
//            char chr = oldRev.charAt(idx - 1)
//            if (chr < 90) {
//                chrAry[idx - 1] = (char)(chr + 1)
//                newRev = chrAry.toString()
//                true
//            } else {
//                if(idx==1){
//                    chrAry[idx - 1] = (char)65
//                    newRev = 'A' + chrAry.toString()
//                    true
//                }
//                chrAry [idx - 1] =(char) 65
//                false
//            }
//        }
//    }
//    println "$oldRev -> $newRev"
//}
def atrId = aw.agileClass.getAttribute('Page Three.*Printer').id
IAgileList list = aw.getCell(atrId).value
def vals = list.selection.collect{it.value}

println vals