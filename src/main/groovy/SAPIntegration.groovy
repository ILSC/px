import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def itmNum = "55545"

def message = JsonOutput.toJson([Request:[number: itmNum], Response: [number:[], description:[]]])
println (message)

def post = 'http://10.6.4.64:50200/RESTAdapter/SI_OB_S_ItemMaster'.toURL().openConnection()
post.requestMethod = 'POST'
post.doOutput = true
post.setRequestProperty('Content-Type', 'application/json')
post.outputStream.write(message.getBytes('UTF-8'))

if(post.responseCode == 200) {
    def out = new JsonSlurper().parse(post.inputStream)
    def desc = out?.MT_ItemMaster?.Response?.description
    if(desc){
        println desc
    }
}
