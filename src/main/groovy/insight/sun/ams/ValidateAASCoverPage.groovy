package insight.sun.ams

import com.agile.agileDSL.ScriptObj.IBaseScriptObj
import com.agile.px.IUpdateTitleBlockEventInfo

import java.util.logging.Logger

class ValidateAASCoverPage {

    Logger logger = Logger.getLogger('insight.sun.ams.ValidateChangeCoverPage')

    Integer ATT_TYPE_OF_GRID = 1549, ATT_MFG_LOC = 1564, ATT_MARKET = 1565, ATT_COUNTRY = 1557

    void invokeScript(IBaseScriptObj obj) {
        def info = obj.PXEventInfo as IUpdateTitleBlockEventInfo
        def co = info.dataObject

        if (info.getCell(ATT_TYPE_OF_GRID) || info.getCell(ATT_TYPE_OF_GRID)) {
            String gridType = info.getCell(ATT_TYPE_OF_GRID)?.value?.toString() ?:
                    co.getValue(ATT_TYPE_OF_GRID).toString()

            String mfgLoc = info.getCell(ATT_MFG_LOC)?.value?.toString() ?:
                    co.getValue(ATT_MFG_LOC).toString()

            if (gridType in ['Contract-Manufacturing/Loan-Licensed', 'Out-Sourced'] && mfgLoc != 'Others')
                throw new Exception("Manufacturing location must be selected as 'Others' for type of grid '$gridType'.")
            obj.logInfo('Validation Passed.')
        } else {
            obj.logInfo('Validation not applicable.')
        }

        if (info.getCell(ATT_MARKET) || info.getCell(ATT_COUNTRY)) {
            Map map = ["Czech Republic-Slovakia": "Czech Republic|Slovakia",
                       "Denmark-Finland-Sweden" : "Denmark|Finland|Sweden",
                       "Denmark-Norway"         : "Denmark|Norway",
                       "Denmark-Norway-Sweden"  : "Denmark|Norway|Sweden",
                       "Finland-Sweden"         : "Finland|Sweden",
                       "Greece-Spain"           : "Greece|Spain",
                       "Norway-Sweden"          : "Norway|Sweden",
                       "USA"                    : "United States"]

            String mkt = info.getCell(ATT_MARKET)?.value?.toString() ?:
                    co.getValue(ATT_MARKET).toString()
            String country = info.getCell(ATT_COUNTRY)?.value?.toString() ?:
                    (co.getValue(ATT_COUNTRY)?.toString() ?: '')
            if (country == '') return

            if (map.containsKey(mkt) && !(country ==~ map.get(mkt)))
                throw new Exception("Country can't be set as $country with market set as '$mkt'.")
            obj.logInfo('Validation Passed.')
        } else {
            obj.logInfo('Validation not applicable.')
        }
    }
}
