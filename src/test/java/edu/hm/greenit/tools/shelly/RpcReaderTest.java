package edu.hm.greenit.tools.shelly;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RpcReaderTest {

    private static final String testResponse = """
            {
               "id": 1,
               "src": "shellyplugsg3",
               "result": {
                 "ble": {},
                 "cloud": {
                   "connected": true
                 },
                 "mqtt": {
                   "connected": false
                 },
                 "plugs_ui": {},
                 "switch:0": {
                   "id": 0,
                   "source": "WS_in",
                   "output": true,
                   "apower": 9.5,
                   "voltage": 237.0,
                   "freq": 50.1,
                   "current": 0.149,
                   "aenergy": {
                     "total": 11009.330,
                     "by_minute": [
                       212.395,
                       0.000,
                       212.395
                     ],
                     "minute_ts": 1743801600
                   },
                   "ret_aenergy": {
                     "total": 0.000,
                     "by_minute": [
                       0.000,
                       0.000,
                       0.000
                     ],
                     "minute_ts": 1743801600
                   },
                   "temperature": {
                     "tC": 41.6,
                     "tF": 106.9
                   }
                 },
                 "sys": {
                   "mac": "123456798",
                   "restart_required": false,
                   "time": "23:20",
                   "unixtime": 1743801611,
                   "uptime": 4259094,
                   "ram_size": 219992,
                   "ram_free": 118688,
                   "fs_size": 1048576,
                   "fs_free": 712704,
                   "cfg_rev": 21,
                   "kvs_rev": 0,
                   "schedule_rev": 0,
                   "webhook_rev": 0,
                   "available_updates": {},
                   "reset_reason": 3
                 },
                 "wifi": {
                   "sta_ip": "123.456.789.101",
                   "status": "got ip",
                   "ssid": "TEST_WIFI",
                   "rssi": -60
                 },
                 "ws": {
                   "connected": false
                 }
               }
             }
            
            """;

    @Test
    public void testParseResponse() throws JsonProcessingException {
        Meter value = RpcReader.parsePowerConsumption(testResponse);
        Assertions.assertEquals(9.5d, value.getPower());
    }

}
