package org.zenboot.portal.processing

import grails.plugin.springsecurity.SpringSecurityUtils
import org.springframework.http.HttpStatus
import org.zenboot.portal.AbstractRestController
import org.zenboot.portal.security.Person
import org.zenboot.portal.security.Role

class ExecutionZoneRestController extends AbstractRestController {

    def springSecurityService
    def accessService
    def scriptDirectoryService
    def executionZoneService
    def applicationEventPublisher

    static allowedMethods = [help: "GET", list: ["GET","POST"], execute: "POST", listparams: "POST", listactions: "POST"]

    //The help method gives you an overview about the possible rest endpoints and which parameters could be set
    def help = {

    }

    //Return a list of enabled execution zones to which the user has access
    // The list is be more specified if an execType param is set
    def list = {

        def results
        ExecutionZoneType executionZoneType

        if (params.execType) {
            if (params.long('execType')) {
                executionZoneType = ExecutionZoneType.findById(params.execType)
            } else if (params.execType instanceof String) {
                executionZoneType = ExecutionZoneType.findByName(params.execType)
            }
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {

            if (executionZoneType) {
                results = ExecutionZone.findAllByTypeAndEnabled(executionZoneType, true)
            }
            else {
                results = ExecutionZone.findAllByEnabled(true)
            }
        }
        else {

            List<ExecutionZone> executionZones = new ArrayList<ExecutionZone>()

            def executionZonesIDs

            if (accessService.accessCache[springSecurityService.getCurrentUserId()]) {
                executionZonesIDs = accessService.accessCache[springSecurityService.getCurrentUserId()].findAll {it.value}
            }
            else {
                accessService.refreshAccessCacheByUser(Person.findById(springSecurityService.getCurrentUserId()))
                executionZonesIDs = accessService.accessCache[springSecurityService.getCurrentUserId()].findAll {it.value}
            }

            executionZonesIDs.each {
               executionZones.add(ExecutionZone.get(it.key))
            }

            if (executionZoneType) {
                results = new ArrayList<ExecutionZone>()

                executionZones.each {zone ->
                    if (zone.type == executionZoneType && zone.enabled) {
                        results.add(zone)
                    }
                }
            }
            else if (executionZoneType == null && params.execType) {
                this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'The requested execution zone type does not exist.')
                return
            }
            else {
                results = executionZones.findAll() {it.enabled}
            }
        }

        withFormat {
            xml {
                render (contentType: "text/xml") {
                    executionzones {
                        results.each { result ->
                            execId result.id
                            execType result.type.name
                            execDescription result.description
                        }
                    }
                }
            }
            json {
                render (contentType:"text/json"){

                    def executionZones = [ executionZones:array {
                        results.each { result ->
                            zone(execId: result.id, execType: result.type.name, execDescription: result.description)
                        }
                    }]

                    executionzones executionZones
                }
            }
        }
    }

    def execute = {

        ExecutionZone executionZone
        String actionName

        if (ExecutionZone.get(params.id)) {
            executionZone = ExecutionZone.get(params.id)
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'executionZone id not set.')
            return
        }

        if (params.actionName) {
            actionName = params.actionName
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'Action name not set.')
            return
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN) || accessService.accessCache[springSecurityService.getCurrentUserId()] ||
                accessService.userHasAccess(executionZone)) {

            File stackDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" + actionName)

            ExecutionZoneAction action = executionZoneService.createExecutionZoneAction(executionZone, stackDir)
            applicationEventPublisher.publishEvent(new ProcessingEvent(action, springSecurityService.currentUser, "REST-call run"))

            this.renderRestResult(HttpStatus.OK, executionZone)
        }
        else {
            this.renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to execute this execution Zone.')
        }
    }

    // the method returns a list of all required parameters of an execution zone
    def listparams = {

        ExecutionZone executionZone
        String actionName

        if (ExecutionZone.get(params.id)) {
            executionZone = ExecutionZone.findById(params.id)
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'executionZone id not set.')
            return
        }

        if (params.actionName) {
            actionName = params.actionName
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'Action name not set.')
            return
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN) || accessService.accessCache[springSecurityService.getCurrentUserId()] != null ?
                accessService.accessCache[springSecurityService.getCurrentUserId()][executionZone.id] :
                accessService.userHasAccess(executionZone)) {

            File stackDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" + actionName)

            def paramsSet = executionZoneService.getExecutionZoneParameters(executionZone, stackDir)

            withFormat {
                xml {
                    render(contentType: "text/xml") {

                    }
                }
                json {
//                    render(contentType: "text/json") { paramsSet as JSON }
                }
            }
        }
        else {
            this.renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to request the parameter for this zone.')
        }
    }

    // this method creates a list of all possible actions for the executionzone
    def listactions = {

        ExecutionZone executionZone
        File scriptDir

        if (ExecutionZone.get(params.id)) {
            executionZone = ExecutionZone.get(params.id)
        }
        else {
            this.renderRestResult(HttpStatus.NOT_FOUND, null, null, 'executionZone id not set.')
            return
        }

        if (SpringSecurityUtils.ifAllGranted(Role.ROLE_ADMIN)) {
            scriptDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" )
        }
        else if(accessService.accessCache[springSecurityService.getCurrentUserId()] != null ?
                accessService.accessCache[springSecurityService.getCurrentUserId()][executionZone.id] :
                accessService.userHasAccess(executionZone)) {

            scriptDir = new File(scriptDirectoryService.getZenbootScriptsDir().getAbsolutePath()
                    + "/" + executionZone.type.name + "/scripts/" )
        }
        else {
            this.renderRestResult(HttpStatus.FORBIDDEN, null, null, 'This user has no permission to request the actions for this zone.')
            return
        }
        File[] scriptDirFiles = scriptDir.listFiles()
        List<String> dirContent = new ArrayList<String>(scriptDirFiles.size())

        scriptDirFiles.each {dirContent.add(it.name)}

        withFormat {
            xml {
                render(contentType: "text/xml") {
                    actions {
                        dirContent.each {
                            action it
                        }
                    }
                }
            }
            json {
                render(contentType: "text/json") {

                    def actions = [actions: array {
                        dirContent.each {
                            action(action: it)
                        }
                    }]

                    executionzoneactions actions
                }
            }
        }
    }
}
