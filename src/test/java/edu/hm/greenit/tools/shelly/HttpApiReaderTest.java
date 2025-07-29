package edu.hm.greenit.tools.shelly;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpApiReaderTest {

    private String testResponse = """
            {
            	"wifi_sta": {
            		"connected": true,
            		"ssid": "TEST_WIFI",
            		"ip": "123.456.789.101",
            		"rssi": -13
            	},
            	"cloud": {
            		"enabled": true,
            		"connected": true
            	},
            	"mqtt": {
            		"connected": false
            	},
            	"time": "17:23",
            	"serial": 1234,
            	"has_update": true,
            	"mac": "123456ABCD",
            	"relays": [
            		{
            			"ison": true,
            			"has_timer": false,
            			"overpower": false
            		}
            	],
            	"meters": [
            		{
            			"power": 70.24,
            			"is_valid": true,
            			"timestamp": 1739294619,
            			"counters": [
            				71.380,
            				72.397,
            				71.324
            			],
            			"total": 18013
            		}
            	],
            	"temperature": 0.00,
            	"overtemperature": false,
            	"tmp": {
            		"tC": 0.00,
            		"tF": 32.00,
            		"is_valid": "true"
            	},
            	"update": {
            		"status": "pending",
            		"has_update": true,
            		"new_version": "20230913-113610/v1.14.0-gcb84623",
            		"old_version": "20191018-113038/master@b12f42e3"
            	},
            	"ram_total": 50824,
            	"ram_free": 37188,
            	"fs_size": 233681,
            	"fs_free": 171935,
            	"uptime": 14883
            }
            """;

    @Test
    public void testParseResponse() throws JsonProcessingException {
        Meter value = HttpApiReader.parsePowerConsumption(testResponse);
        Assertions.assertEquals(70.24d, value.getPower());
    }

}
