import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.IAttachmentRow
import com.agile.api.IChange
import com.agile.api.IDataObject
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.px.IObjectEventInfo
import groovy.xml.MarkupBuilder
import insight.common.logging.JLogger
import insight.sun.ams.AMSConfiguration

import javax.mail.BodyPart
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.util.logging.Level
import java.util.logging.Logger

import groovy.xml.XmlParser
import groovy.xml.XmlUtil

import static com.agile.api.ChangeConstants.TABLE_AFFECTEDITEMS
import static com.agile.api.CommonConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE
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

Iterator<Object> processAAS(IDataObject aas) {
    aas.getTable(TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
        sendMail(aw, readKey('sendAWInfo.emails'))
    }
}

def sendMail(IItem item, List<String> emails) {
    Properties properties = System.properties
    properties.setProperty('mail.smtp.host', 'Ncorp-hup-01.ranbaxy.com')
    properties.setProperty('mail.smtp.port', '25')
    Session session = Session.getDefaultInstance(properties)

    MimeMessage msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress('agile.admin@sunpharma.com'))
    emails.each { email ->
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email))
    }
    msg.setSubject("Item Code Generation - $item.name")

    BodyPart messageBodyPart = new MimeBodyPart()
    messageBodyPart.setContent(getArtworkSpecifications(item), "text/html")
    Multipart multipart = new MimeMultipart()
    multipart.addBodyPart(messageBodyPart)

    BodyPart messageAttachment = new MimeBodyPart()
    messageAttachment.attachFile(generateFile(item))
    multipart.addBodyPart(messageAttachment)

    msg.setContent(multipart)
    Transport.send(msg)
}

File generateFile(){
    Node xml = new XmlParser().parse(readKey('sendAWInfo.templatePath'))
    def cellList = xml.Worksheet.Table.Row.first().Cell.asList()
    def headerMap = cellList.collectEntries {
        [(it.Data.text()): cellList.indexOf(it)]
    }
    def tbl = xml.Worksheet.Table.first()
    tbl.append(new NodeBuilder().Row { headerMap.collect { k, v -> Cell { Data('ss:Type': 'String', k) } } })
    new File(getParentDir(), aw.name + '.xls') << XmlUtil.serialize(xml)
}

def getArtworkSpecifications(IItem item) {
    def atrMap = ['TitleBlock.Number',
                  'TitleBlock.Description',
                  'TitleBlock.Rev Release Date',
                  'TitleBlock.Tentative Effectivity Date',
                  'PageTwo.Component Type',
                  'PageThree.Product Name/Brand Name',
                  'PageThree.*Generic Name',
                  'PageThree.*Market',
                  'PageThree.*Manufacturing Location',
                  'PageThree.*Strength',
                  'PageThree.*Dosage Form',
                  'PageThree.*Pack Size',
                  'PageThree.*Dimension/Actual Size',
                  'PageThree.*Board/Substrate',
                  'PageThree.*Grammage/GSM',
                  'PageThree.*Design/Style/Folding Pattern',
                  'PageThree.*Pantone Shade'].collectEntries { a ->
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

File getParentDir(){
    File file = new File(readKey('sendAWInfo.staging'))
    file.mkdirs()
    file
}
