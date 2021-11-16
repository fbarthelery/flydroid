Flydroid
========

Flydroid is a self hosted platform for Android emulation.

It uses [Nomad](https://www.nomadproject.io/) as an orchestrator to manage Docker containers provided by 
[Android emulator container scripts](https://github.com/google/android-emulator-container-scripts). 
It also provides an API build on top of Nomad to launch and stop virtual devices.


Installation
============

Android jobs
-------------

You can create the Nomad job definitions for each of your Android docker images. A template is provided
[here](templates/android-job). You can adapt it to fit your needs.

You can follow [these instructions](https://github.com/google/android-emulator-container-scripts)  to 
create the Android docker images that you want to run or use the [hosted containers](https://github.com/google/android-emulator-container-scripts/blob/master/REGISTRY.MD)


API Server job
--------------

A template of the Nomad job definition for the API server is provided [here](templates/flydroid-api.nomad)
You can adapt it to fit your needs.


REST API server
===============

The REST API can be launch as a Nomad job. You will need to provide the following environment variables:

    - NOMAD_URL : the URL of your Nomad instance
    - API_KEYS_DB: optional file containing api keys
    - NOMAD_DEVICES: a file mapping Flydroid device names to nomad job id
    - NOMAD_TOKEN: a Nomad token to access the Nomad API. See [here]() for more information

	The nomad token needs the following capabilities :
```
	    namespace "default" {
		    capabilities = ["dispatch-job", "submit-job", "read-job", "list-jobs"]
	    }
```


API keys
--------

The api keys file contains a list of api keys in this format:
```
keys = [
  "my first api key",
  "my second key"
]

```

Nomad devices
--------

The nomad devices file contains a map of Flydroid device names to nomad job id in this format:
```
devices {
  my-device = "my-nomad-job"
  android-p = "android-p"
  android-q = "android-q"
}

```

You can then use the Api to start an instance of the `my-device` device.

API
===

Authentication
--------------

When API keys are enabled, an API key should be provided to API requests by using the `X-FLYDROID-KEY` header.


Start a virtual device
----------------------

The `/start` endpoint is used to start a virtual device.

| Method  | Path   | Accepts          |  Produces        |
| ------- | -------|------------------|----------------- |
| POST    | /start | application/json | application/json |


| Parameters | Description                                   |
| ---------- | --------------------------------------------- |
| image      | The name of the Android job you want to start |
| name       | A name for your virtual device                |
| adbkey     | The private key for the adb server            |

Sample payload

```
{
    "name": "My Android P device",
    "image": "android-p",
    "adbkey": "-----BEGIN PRIVATE KEY-----MIIEvgIB cut here mgsAmLrxRRidSLi3P/dl v+ogCZB1BF4B0M/IpdkDMGfO-----END PRIVATE KEY----"
}

```

Response

The `/start` endpoint replies with a Virtual device definition

| Field | Description                                        |
| ---------- | --------------------------------------------- |
| id         | id of the virtual device                      |
| ip         | IP address of  virtual device                 |
| adbPort    | port of the adb service                       |
| consolePort | port of the console service                  |
| grpcPort   | port of the grpc service                      |


Sample Response

```
{
    "id": "ad3d9cf6-0a3b-48bb-aa6a-df4b65e00235",
    "ip": "10.0.0.2",
    "adbport": 8887,
    "consolePort": 8888,
    "grpcPort": 8889
}
```

Stop a virtual device
----------------------

The `/devices/{deviceId}` endpoint or the `/devices/by-name/{deviceName}` are used to stop a virtual device.

| Method  | Path                  | Accepts          |  Produces        |
| ------- | --------------------- | ---------------- | ---------------- |
| DELETE    | /devices/{deviceId} | application/json | application/json |
| DELETE    | /devices/by-name/{deviceName} | application/json | application/json |


| Parameters | Description                                   |
| ---------- | --------------------------------------------- |
| deviceId   | The id of the virtual device to stop          |
| deviceName | The name of the virtual device to stop        |

Response

On successful stop, the endpoint replies with the Virtual device definition of the device stopped.


Retrieve a virtual device definition
-------------------------------------

The `/devices/{deviceId}` endpoint can be used to retrieve a virtual device definition.

| Method  | Path                  | Accepts          |  Produces        |
| ------- | --------------------- | ---------------- | ---------------- |
| GET     | /devices/{deviceId} | application/json | application/json |


| Parameters | Description                                   |
| ---------- | --------------------------------------------- |
| deviceId   | The id of the virtual device to stop          |

Response

The endpoint replies with the Virtual device definition of the device.


