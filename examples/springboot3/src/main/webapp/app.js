/*
 * Copyright 2009-2025 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
document.addEventListener("DOMContentLoaded", () => {
    let c = document.getElementById("console")
    function debug(msg) {
        c.appendChild(document.createElement("span")).innerHTML = `${msg}\n`
    }

    const j = new Jolokia("/jolokia")

    function clear() {
        c.innerHTML = ""
    }

    function version() {
        j.version().then(j => {
            debug(JSON.stringify(j))
        })
    }

    function listDomain() {
        j.search(null).then(j => {
            let beans = []
            j.forEach(mbean => {
                beans.push(mbean)
            })
            beans.sort()
            beans.forEach(mbean => {
                debug(` - ${mbean}`)
            })
        })
    }

    function stats() {
        j.getAttribute("jolokia.example:type=Standard", "Statistics").then(j => {
            debug(JSON.stringify(j))
        })
    }
    function statsSpring() {
        j.getAttribute("jolokia.example:type=SpringManagedResource", "Statistics").then(j => {
            debug(JSON.stringify(j))
        })
    }
    function statsJsonMBean() {
        j.getAttribute("jolokia.example:type=JsonMBean", "Statistics").then(j => {
            debug(JSON.stringify(j))
        })
    }
    function setStatsJsonMBean() {
        const data = document.getElementById("statistics-editor").value
        j.setAttribute("jolokia.example:type=JsonMBean", "Statistics", data).then(j => {
            debug(`Value changed from ${JSON.stringify(j)} to ${data}`)
        }).catch(e => {
            debug(`Error: ${e}`)
        })
    }

    debug("Jolokia test application started")

    document.getElementById("clear").addEventListener("click", clear)
    document.getElementById("version").addEventListener("click", version)
    document.getElementById("search").addEventListener("click", listDomain)
    document.getElementById("stats").addEventListener("click", stats)
    document.getElementById("stats-spring").addEventListener("click", statsSpring)
    document.getElementById("stats-json").addEventListener("click", statsJsonMBean)
    document.getElementById("set-stats-json").addEventListener("click", setStatsJsonMBean)
})
