package insight.sun.ams

import groovy.json.JsonSlurper
import insight.common.logging.JLogger

import java.util.logging.Logger

class AMSConfiguration {
    static def config
    static long timestamp = 0
    static Logger logger = JLogger.getLogger(AMSConfiguration.class.name)

    static String path

    static {
        path = System.getenv('INSIGHT_APPLICATION_CONFIG') ?: System.getProperty('insight.application.config') ?:
                new File('config').exists() ? 'config' : '.'
        loadCfg()
    }

    static readConfiguration(File cfgFile) {
        try {
            logger.info("Reading config. Last Modification: ${new Date(cfgFile.lastModified())}")
            config = new JsonSlurper().parse(new FileInputStream(cfgFile))
            timestamp = cfgFile.lastModified()
        } catch (Exception ex) {
            throw new Exception('Failed to read AMS configuration.', ex)
        }
    }

    static loadCfg() {
        File cfgFile = new File(path, 'amsConfig.json')
        if (cfgFile.exists() && cfgFile.canRead()) {
            if (cfgFile.lastModified() != timestamp) {
                readConfiguration(cfgFile)
            }
            config
        } else {
            throw new Exception("Failed to read AMS configuration. Configuration file could not be located at $cfgFile.absolutePath.")
        }
    }

    static readKey(String key, def defaultVal) {
        readKey(key, true) ?: defaultVal
    }

    static readKey(String key, boolean ignoreError = false) {
        def base = config
        try {
            key.split('\\.').each {
                base = base["$it"]
            }
            base
        } catch (Exception ex) {
            String msg = "Failed to read key $key from AMS config"
            logger.severe(msg)
            if (ignoreError)
                return null
            else
                throw (new Exception(msg, ex))
        }
    }
}

