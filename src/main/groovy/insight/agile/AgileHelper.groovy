package insight.agile

import com.agile.api.APIException
import com.agile.api.IAgileSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.agile.api.AgileSessionFactory.*

class AgileHelper {

    private static Logger logger = LoggerFactory.getLogger(AgileHelper.class)

    IAgileSession session

    AgileServerInfo serverInfo

    private IAgileSession connect() {
        if (serverInfo?.valid) {
            session = null
            try {
                session = getInstance(serverInfo.URL).createSession([(USERNAME): serverInfo.username, (PASSWORD): serverInfo.password])
            } catch (APIException e) {
                logger.error('Connection to Agile PLM Failed', e)
            }
        } else {
            logger.error('Invalid Server Configuration. Connection to Agile PLM Failed')
        }
        session
    }

    IAgileSession getSession() {
        this.@session?.isOpen() ? this.@session : connect()
    }
}

