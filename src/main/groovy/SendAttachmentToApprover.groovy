import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.api.*
import com.agile.px.IObjectEventInfo
import groovy.xml.MarkupBuilder

import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

void invokeScript(IBaseScriptObj obj) {
    IObjectEventInfo info = obj.PXEventInfo
    IChange aas = info.dataObject
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
    Properties properties = System.properties
    properties.setProperty('mail.smtp.host', 'Ncorp-hup-01.ranbaxy.com')
    properties.setProperty('mail.smtp.port', '25')
    Session session = Session.getDefaultInstance(properties)

    MimeMessage msg = new MimeMessage(session)
    msg.setFrom(new InternetAddress('agile.admin@sunpharma.com'))
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

def getArtworkAttachments(IItem item) {
    item.attachments.findAll { IRow r ->
        r.getValue(ItemConstants.ATT_ATTACHMENTS_ATTACHMENT_TYPE).toString() in ['Draft File', 'Process File']
    }
}