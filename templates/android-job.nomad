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

# This file is a Nomad job definition to run an Android P docker container.
# You can use it as a based for your own android jobs
job "android-p" {
  # The "region" parameter specifies the region in which to execute the job.
  # If omitted, this inherits the default region name of "global".
  # region = "global"
  #
  # The "datacenters" parameter specifies the list of datacenters which should
  # be considered when placing this task. This must be provided.
  datacenters = ["dc1"]

  type = "batch"

  parameterized {
      meta_required = ["adbkey", "name"]
  }

  group "android-p" {
    count = 1
    restart {
      # The number of attempts to run the job within the specified interval.
      attempts = 1
      interval = "30m"
      delay = "15s"
      mode = "fail"
    }

    ephemeral_disk {
      size = 300
    }

    task "android" {
      driver = "docker"
      config {
        # The android emulator container script provides some images listed here
        # https://github.com/google/android-emulator-container-scripts/blob/master/REGISTRY.MD
        # You can also build your own Android image and use it
        image = "us-docker.pkg.dev/android-emulator-268719/images/p-playstore-x64-no-metrics:30.0.23"
        devices = [
          {
            host_path = "/dev/kvm"
            container_path = "/dev/kvm"
          }]
        port_map = {
          adb = 5555
          console = 5554
          grpc = 8554
        }
      }

      env {
        ADBKEY = "${NOMAD_META_ADBKEY}"
      }

      # The "resources" stanza describes the requirements a task needs to
      # execute. Resource requirements include memory, network, cpu, and more.
      # This ensures the task will execute on a machine that contains enough
      # resource capacity.
      #
      # For more information and examples on the "resources" stanza, please see
      # the online documentation at:
      #
      #     https://www.nomadproject.io/docs/job-specification/resources.html
      #
      resources {
        # You may have to adjust the resources based on the Android image and your system
        cpu    = 4000 # 4000 MHz
        memory = 6000 # 6000MB

        network {
          mbits = 10
          port  "adb"  { }
          port  "console"  { }
          port  "grpc"  { }
        }
      }

      service {
        name = "android-p"
        tags = ["global", "android"]
        port = "adb"

        check {
          name     = "alive"
          type     = "tcp"
          interval = "1h"
          timeout  = "20s"
        }
      }
    }
  }
}
