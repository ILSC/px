import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.ChangeConstants
import com.agile.api.IAttachmentRow
import com.agile.api.IChange
import com.agile.api.IItem
import com.agile.api.IRow
import com.agile.api.IUser
import com.agile.api.ItemConstants
import com.agile.api.UserConstants
import com.agile.px.IObjectEventInfo
import groovy.xml.MarkupBuilder
import javax.mail.*
import javax.mail.internet.*

import static insight.sun.ams.AMSConfiguration.loadCfg
import static insight.sun.ams.AMSConfiguration.readKey

void invokeScript(IBaseScriptObj obj) {
    IObjectEventInfo info = obj.PXEventInfo
    IChange aas = info.dataObject
    loadCfg()

    List<String> emails = aas.getTable(ChangeConstants.TABLE_WORKFLOW).findAll { IRow r ->
        r.getValue(ChangeConstants.ATT_WORKFLOW_WORKFLOW_STATUS).toString() == 'Commercial Team Review' &&
                r.getValue(ChangeConstants.ATT_WORKFLOW_REVIEWER).toString() in ['AMS-Commercial Team', 'AMS-Commerical Team'] &&
                r.getValue(ChangeConstants.ATT_WORKFLOW_SIGNOFF_USER) != null
    }.collect { r ->
        IUser user = r.getCell(ChangeConstants.ATT_WORKFLOW_SIGNOFF_USER).referent
        user.getValue(UserConstants.ATT_GENERAL_INFO_EMAIL).toString()
    }
    aas.getTable(ChangeConstants.TABLE_AFFECTEDITEMS).referentIterator.each { IItem aw ->
        sendMail(aw, emails)
    }
    rows.find { r ->
        IUser user = r.getCell(ChangeConstants.ATT_WORKFLOW_SIGNOFF_USER).referent
        String emailId = user.getValue(UserConstants.ATT_GENERAL_INFO_EMAIL)
        sendMail(aw, emailId)
        obj.logMonitor("Artwork details sent to $emailId")
    }
}

def sendMail(IItem item, List<String> emails) {
    def smtpConfig = readKey('smtpConfig')
    Properties properties = System.properties
    properties.setProperty('mail.smtp.host', smtpConfig.host)
    properties.setProperty('mail.smtp.port', smtpConfig.port)
    Session session = Session.getDefaultInstance(properties)
    MimeMessage msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress(smtpConfig.from))
    emails.each { email ->
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(email))
    }
    msg.setSubject("Artwork Specifications - $item.name")

    BodyPart messageBodyPart = new MimeBodyPart()
    messageBodyPart.setContent(getArtworkSpecifications(item), "text/html")
    Multipart multipart = new MimeMultipart()
    multipart.addBodyPart(messageBodyPart)
    List<IAttachmentRow> attachments = getArtworkAttachments(item)
    attachments?.each { ar ->
        File file = new File(ar.name)
        file.delete()
        file.append(ar.file)
        messageBodyPart = new MimeBodyPart()
        messageBodyPart.attachFile(file)
        multipart.addBodyPart(messageBodyPart)
    }

    msg.setContent(multipart)
    Transport.send(msg)
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

def getArtworkAttachments(IItem item) {
    item.attachments.findAll { IRow r ->
        r.getValue(ItemConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString() in ['Draft File', 'Process File']
    }
}