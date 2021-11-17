# Flydroid is a self hosted platform for Android emulation.
#
# Copyright (C) 2020-2021 by Frederic-Charles Barthelery.
#
# This file is part of Flydroid.
#
# Flydroid is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Flydroid is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Flydroid.  If not, see <http://www.gnu.org/licenses/>.

# This file is a Nomad job definition to run Flydroid API.
job "flydroid-api" {
  # The "region" parameter specifies the region in which to execute the job.
  # If omitted, this inherits the default region name of "global".
  # region = "global"
  #
  # The "datacenters" parameter specifies the list of datacenters which should
  # be considered when placing this task. This must be provided.
  datacenters = ["dc1"]

  type = "service"

  group "api" {
    count = 1
    restart {
      # The number of attempts to run the job within the specified interval.
      attempts = 2
      interval = "30m"
      delay = "15s"
      mode = "fail"
    }

    ephemeral_disk {
      size = 300
    }

    network {
      mode = "bridge"
      port  "http"  {
      	static = 23578
      }
    }

    task "api" {
      driver = "docker"

      config {
        image = "openjdk:11.0-jre"
        command = "java"
        args = [ "-jar", "local/apiserver-0.1-SNAPSHOT-all.jar" ]
      }

      env {
        PORT = "${NOMAD_PORT_http}"
        NOMAD_URL = "http://your.nomad.server.url"
        NOMAD_TOKEN = "your-nomad-server-token"
        API_KEYS_DB = "local/apiKeys.conf"
        NOMAD_DEVICES = "local/devices.conf"
      }

      template {
        destination = "local/apiKeys.conf"
        data = <<EOH
	    keys = [
          "my first api key",
          "my second key"
        ]
	  EOH
      }

      template {
        destination = "local/devices.conf"
        data = <<EOH
	    # map of flydroid device name to nomad job id
	    devices {
	      android-n = "android-n"
	      android-p = "android-p"
	      android-q = "android-q"
	    }
	  EOH
      }

      artifact {
        source      = "https://github.com/fbarthelery/flydroid/releases/download/0.1/apiserver-0.1-all.jar"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 192 # 192MB
      }
    }

    service {
      name = "flydroid-api"
      tags = ["global", "android"]
      port = "http"

      connect {
        sidecar_service {
          proxy {
            upstreams {
              destination_name = "nomad"
              local_bind_port = 1234
            }
          }
        }
      }

      check {
        name     = "alive"
        type     = "http"
        path	   = "/"
        interval = "10m"
        timeout  = "2s"
      }
    }
  }
}
