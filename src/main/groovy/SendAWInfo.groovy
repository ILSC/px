import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.px.IObjectEventInfo
import groovy.xml.XmlUtil
import insight.common.logging.JLogger

import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.util.logging.Level
import java.util.logging.Logger

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    Logger logger = JLogger.getLogger('insight.sun.ams.DeleteOnRejection')
    try {
        IObjectEventInfo eventInfo = obj.PXEventInfo
        def aas = eventInfo.dataObject
        processAAS(aas)
    } catch (Exception ex) {
        obj.logFatal([ex.message, ex.cause?.message].join(' '))
        logger.log(Level.SEVERE, 'Failed to remove process files on Artworks attached to AAS', ex)
        throw (ex)
    }
}

void processAAS(IDataObject aas) {
    def cfg = readKey('sendAWInfo')
    if (aas.getValue('Page Three.*Manufacturing Location')?.toString()?.split(';')?.toList()?.intersect(cfg.mfgLocList)) {
        File outFile = generateFile(aas, cfg)
        sendMail(aas, outFile, cfg)
    }
}

def sendMail(IChange aas, File outFile, def config) {
    Properties properties = System.properties
    properties.setProperty('mail.smtp.host', '172.30.58.249')
    properties.setProperty('mail.smtp.port', '25')
    Session session = Session.getDefaultInstance(properties)

    MimeMessage msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress('agile.admin@sunpharma.com'))
    config.emails.each { email ->
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email))
    }
    msg.setSubject("Item Code Generation - $aas.name")

    BodyPart messageBodyPart = new MimeBodyPart()
    messageBodyPart.setContent(buildMessageBody(aas), "text/html")
    Multipart multipart = new MimeMultipart()
    multipart.addBodyPart(messageBodyPart)

    BodyPart messageAttachment = new MimeBodyPart()
    messageAttachment.attachFile(outFile)
    multipart.addBodyPart(messageAttachment)

    msg.setContent(multipart)
    Transport.send(msg)
}

File generateFile(IChange aas, def config) {
    def xml = new XmlParser().parse(config.templatePath)
    def cellList = xml.Worksheet.Table.Row.first().Cell.asList()
    def headerMap = cellList.collectEntries {
        [(it.Data.text()): cellList.indexOf(it)]
    }
    def tbl = xml.Worksheet.Table.first()
    int i = 1
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
        boolean isFoil = aw.getValue('Page Two.*Component Type').toString().toLowerCase().startsWith('foil')
        def map = isFoil ? config.mapping.foil : config.mapping.other
        tbl.append(new NodeBuilder().Row { headerMap.collect { k, v -> Cell { Data('ss:Type': 'String', getValue(aw, k, i, map)) } } })
        i++
    }

    File outFile = new File(getParentDir(config.stagingDir), aas.name + '.xls')
    outFile.write(XmlUtil.serialize(xml))
    outFile
}

def getValue(IItem aw, def atrId, int rowIdx, Map mapping) {
    if(atrId == 'S. N0.')
        return rowIdx
    def atr = mapping.get(atrId)
    if (atr) {
        (atr.field ? aw.getValue(atr.field) : atr.value) ?: ''
    } else {
        'Mapping not found in config'
    }
}

def buildMessageBody(IChange aas) {
//    def sw = new StringWriter()
//    MarkupBuilder h = new MarkupBuilder(sw)
//    h {
//        p ( class:'MsoNormal' )
//        table(style: 'width: 90%;') {
//            thead {
//                tr {
//                    th(style: 'width: 35%', 'Attribute')
//                    th(style: 'width: 65%', 'Value')
//                }
//            }
//            tbody {
//                atrMap.each { k, v ->
//                    tr {
//                        th(style: 'width: 35%', k)
//                        th(style: 'width: 65%', v)
//                    }
//                }
//            }
//        }
//    }
//    sw.toString()
    '''<p class="MsoNormal">Hi,<br>
This is an auto-generated mail from AMS<br>
Please find ERP Code Intimation for the Products <br>
1. If Track Shelf Life is "YES" / 24 MONTHS, it means Track Shelf Life is "YES" and Shelf Life is "24 MONTHS"<br>
<br>
2. If Track Shelf Life is "NO", It means Track Shelf Life is "NO".<u></u><u></u></p>'''
}


File getParentDir(String stagingDir) {
    File file = new File(stagingDir)
    file.mkdirs()
    file
}
