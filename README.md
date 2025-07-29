# shelly-power-reader

This is a Quarkus-based CLI tool to continuously read power and energy data from shelly devices.

## 🚀 Usage

### 🔧 Prerequisites

* Java 17+
* A shelly device 

### 🚪 Running the Application

The reader can be used as follows:


```bash
java -jar .\shelly-power-reader-1.0-runner.jar --ip <shelly_ip> --generation <shelly_generation> --password <shelly_password>
```

Where the parameters are the following:

- **shelly_ip**: the IP where your shelly device is reachable over the network
- **shelly_generation**: which Shelly device generation do you use, currently two settings are supported: 1 and 2+. When setting the shelly_generation to 2+, the RPC protocol is ued ( https://shelly-api-docs.shelly.cloud/gen2/General/RPCProtocol/) which also works with Gen 3 devices.
- **shelly_password**: If your device is protected with a password, you can set it here.

## 📄 Output Format

The shelly power reader generates the following output, which is structured as follows:

```txt
ip,timestamp,power,energy
123.456.789.101,1753786427,10.8,37156.946
123.456.789.101,1753786428,10.7,37156.946
123.456.789.101,1753786429,10.7,37156.946
123.456.789.101,1753786430,10.8,37156.946
```


