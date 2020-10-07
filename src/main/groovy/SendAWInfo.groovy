import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.px.IObjectEventInfo
import groovy.xml.MarkupBuilder
import groovy.util.XmlParser
import groovy.util.XmlUtil
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
        aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
            sendMail(aw, cfg)
        }
    }
}

def sendMail(IItem item, def config) {
    Properties properties = System.properties
    properties.setProperty('mail.smtp.host', '172.30.58.249')
    properties.setProperty('mail.smtp.port', '25')
    Session session = Session.getDefaultInstance(properties)

    MimeMessage msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress('agile.admin@sunpharma.com'))
    config.emails.each { email ->
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email))
    }
    msg.setSubject("Item Code Generation - $item.name")

    BodyPart messageBodyPart = new MimeBodyPart()
    messageBodyPart.setContent(getArtworkSpecifications(item), "text/html")
    Multipart multipart = new MimeMultipart()
    multipart.addBodyPart(messageBodyPart)

    BodyPart messageAttachment = new MimeBodyPart()
    messageAttachment.attachFile(generateFile(item, config))
    multipart.addBodyPart(messageAttachment)

    msg.setContent(multipart)
    Transport.send(msg)
}

File generateFile(IItem aw, def config) {
    def xml = new XmlParser().parse(config.templatePath)
    def cellList = xml.Worksheet.Table.Row.first().Cell.asList()
    def headerMap = cellList.collectEntries {
        [(it.Data.text()): cellList.indexOf(it)]
    }
    def tbl = xml.Worksheet.Table.first()
    tbl.append(new NodeBuilder().Row { headerMap.collect { k, v -> Cell { Data('ss:Type': 'String', getValue(aw, k, config.mapping)) } } })
    File outFile = new File(getParentDir(config.stagingDir), aw.name + '.xls')
    PrintWriter pWriter = new PrintWriter(outFile)
    def printer = new XmlNodePrinter(pWriter)
    printer.print(xml)
    outFile
}

def getValue(IItem aw, def atrId, Map mapping) {
    boolean isFoil = aw.getValue('Page Two.*Component Type').toString().toLowerCase().startsWith('foil')
    def atr = isFoil ? mapping.foil.get(atrId) : mapping.other.get(atrId)
    if (atr) {
        (atr.field ? aw.getValue(atr.field) : atr.value) ?: ''
    } else {
        return 'Mapping not found in config'
    }
}

def getArtworkSpecifications(IItem item) {
    def atrMap = ['Title Block.Number',
                  'Title Block.Description',
                  'Title Block.Rev Release Date',
                  'Title Block.Tentative Effectivity Date',
                  'Page Two.*Component Type',
                  'Page Three.*Product Name/Brand Name',
                  'Page Three.*Generic Name',
                  'Page Three.*Market',
                  'Page Three.*Manufacturing Location',
                  'Page Three.*Strength',
                  'Page Three.*Dosage Form',
                  'Page Three.*Pack Size',
                  'Page Three.*Dimension/Actual Size',
                  'Page Three.*Board/Substrate',
                  'Page Three.*Grammage/GSM',
                  'Page Three.*Design/Style/Folding Pattern',
                  'Page Three.*Pantone Shade'].collectEntries { a ->
        def atr = item.agileClass.getAttribute(a)
        [(atr.name): item.getValue(atr.id)]
    }

    def sw = new StringWriter()
    MarkupBuilder h = new MarkupBuilder(sw)
    h {
        p { strong { span(style: 'font-size: 18px;', 'Artwork Specification') } }
        table(style: 'width: 90%;') {
            thead {
                tr {
                    th(style: 'width: 35%', 'Attribute')
                    th(style: 'width: 65%', 'Value')
                }
            }
            tbody {
                atrMap.each { k, v ->
                    tr {
                        th(style: 'width: 35%', k)
                        th(style: 'width: 65%', v)
                    }
                }
            }
        }
    }
    sw.toString()
}


File getParentDir(String stagingDir) {
    File file = new File(stagingDir)
    file.mkdirs()
    file
}
