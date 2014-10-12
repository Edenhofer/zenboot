package org.zenboot.portal.processing

import grails.converters.JSON
import grails.converters.XML

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.zenboot.portal.ControllerUtils
import org.zenboot.portal.RestResult
import org.zenboot.portal.processing.flow.ScriptletBatchFlow
import org.zenboot.portal.processing.meta.MetadataParameterComparator
import org.zenboot.portal.processing.meta.ParameterMetadata

class ExecutionZoneController implements ApplicationEventPublisherAware {

    def applicationEventPublisher
    def executionZoneService
    def springSecurityService

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    def execute = { ExecuteExecutionZoneCommand cmd ->
        flash.action = 'execute'
        cmd.setParameters(params.parameters)
        log.info("cmd setParameters:" + params.inspect())
        if (cmd.hasErrors()) {
            chain(action:"show", id:cmd.execId, model:[cmd:cmd])
            return
        } else {
            ExecutionZoneAction action = cmd.getExecutionZoneAction()
            this.applicationEventPublisher.publishEvent(new ProcessingEvent(action, springSecurityService.currentUser, params.comment))
            flash.message = message(code: 'default.created.message', args: [message(code: 'executionZoneAction.label', default: 'ExecutionZoneAction'), action.id])
        }

        redirect(action:"show", id:cmd.execId)
    }

    def ajaxGetParameters = { GetExecutionZoneParametersCommand cmd ->
        if (cmd.hasErrors()) {
            return render(view:"/ajaxError", model:[result:cmd])
        }
        try {
            def metadataParams = cmd.getExecutionZoneParameters()
            [
                executionZoneParameters: metadataParams,
                executionZoneParametersEmpty: metadataParams.findAll { ParameterMetadata metadataParam ->
                  metadataParam.value == ""
                },
                executionZoneParametersNonempty: metadataParams.findAll { ParameterMetadata metadataParam ->
                  metadataParam.value != ""
                },
                containsInvisibleParameters: metadataParams.any { ParameterMetadata metadataParam ->
                  !metadataParam.visible
                }
            ]
        } catch (MultipleCompilationErrorsException exc) {
            return render(view:"ajaxScriptCompilationError", model:[exception:exc])
        }
    }

    def ajaxGetFlowChart = { GetScriptletBatchFlow cmd ->
        if (cmd.hasErrors()) {
            return render(view:"/ajaxError", model:[result:cmd])
        }
        try {
            def flow = cmd.getScriptletBatchFlow()
            [flow:flow]
        } catch (MultipleCompilationErrorsException exc) {
            return render(view:"ajaxScriptCompilationError", model:[exception:exc])
        }
    }

    def ajaxGetReadme = { GetReadmeCommand cmd ->
        if (cmd.hasErrors()) {
            return render(view:"/ajaxError", model:[result:cmd])
        }
        [
            scriptDir: cmd.scriptDir,
            markdown: cmd.getReadmeMarkdown(),
            checksum: cmd.getReadmeChecksum(),
            editorId: cmd.editorId
        ]
    }

    def ajaxUpdateReadme = { UpdateReadmeCommand cmd ->
        def result = new RestResult()
        if (cmd.hasErrors()) {
            response.status = HttpStatus.BAD_REQUEST.value()
            result.status = HttpStatus.BAD_REQUEST.value()
            result.message = cmd.errors.getGlobalErrors()*.getCode().collect {
                message(code:it)
            }.join("\n")
        } else {
            cmd.updateReadme()
            result.status = HttpStatus.OK.value()
            result.value = cmd.getReadmeChecksum()
            result.message = message(code:'executionZone.readme.update')
        }
        request.withFormat {
            xml { render result as XML }
            json { render result as JSON }
        }
    }

    def createExposedAction = { ExposeExecutionZoneCommand cmd ->
        cmd.setParameters(params.parameters)
        if (cmd.hasErrors()) {
            chain(action:"show", id:cmd.execId, model:[cmd:cmd])
            return
        }
        chain(controller:'exposedExecutionZoneAction', action:'create', model:['exposedExecutionZoneActionInstance':cmd.executionZoneAction])
    }

    def index() {
        redirect(action: "list", params: params)
    }

    def list() {
        params.max = Math.min(params.max ? params.int('max') : 15, 100)
        if (!params.sort) {
            params.sort = "enabled"
        }
        if (!params.order) {
            params.order = "desc"
        }
        def enabled = false
        if (!params.disabled) {
            enabled = true
        }

        [
            executionZoneInstanceList: ExecutionZone.findAllByEnabled(enabled, params),
            executionZoneInstanceTotal: ExecutionZone.countByEnabled(enabled),
            executionZoneTypes: org.zenboot.portal.processing.ExecutionZoneType.list()
        ]
    }

    def create() {
        return [executionZoneInstance: new ExecutionZone(params), executionZoneTypes:ExecutionZoneType.list()]
    }

    def save() {
        ExecutionZone executionZoneInstance = new ExecutionZone(params)
        executionZoneInstance.enableExposedProcessingParameters = (params.enableExposedProcessingParameters != null)
        ControllerUtils.synchronizeProcessingParameters(ControllerUtils.getProcessingParameters(params), executionZoneInstance)
        if (!executionZoneInstance.save(flush: true)) {
            render(view: "create", model: [executionZoneInstance: executionZoneInstance, executionZoneTypes:ExecutionZoneType.list()])
            return
        }


        flash.message = message(code: 'default.created.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), executionZoneInstance.id])
        redirect(action: "show", id: executionZoneInstance.id)
    }

    def show() {
        def executionZoneInstance = ExecutionZone.get(params.id)
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.id])
            redirect(action: "list")
            return
        }
        List scriptDirs = this.executionZoneService.getScriptDirs(executionZoneInstance.type)

        def structuredScriptDirs = [:]
        structuredScriptDirs["create"] = this.executionZoneService.getScriptDirs(executionZoneInstance.type,"create")
        structuredScriptDirs["update"] = this.executionZoneService.getScriptDirs(executionZoneInstance.type,"update")
        structuredScriptDirs["delete"] = this.executionZoneService.getScriptDirs(executionZoneInstance.type,"delete")
        structuredScriptDirs["misc"]   = this.executionZoneService.getScriptDirs(executionZoneInstance.type,"misc")

        [
            executionZoneInstance: executionZoneInstance,
            scriptDirs: scriptDirs,
            structuredScriptDirs: structuredScriptDirs
        ]
    }

    def edit() {
        def executionZoneInstance = ExecutionZone.get(params.id)
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.id])
            redirect(action: "list")
            return
        }
        [executionZoneInstance: executionZoneInstance,, executionZoneTypes:ExecutionZoneType.list()]
    }

    def update() {
        def executionZoneInstance = ExecutionZone.get(params.id)
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.id])
            redirect(action: "list")
            return
        }

        if (params.version) {
            def version = params.version.toLong()
            if (executionZoneInstance.version > version) {
                executionZoneInstance.errors.rejectValue("version", "default.optimistic.locking.failure",
                [message(code: 'executionZone.label', default: 'ExecutionZone')] as Object[],
                "Another user has updated this ExecutionZone while you were editing")
                flash.action = 'update'
                render(view: "show", model: [executionZoneInstance: executionZoneInstance])
                return
            }
        }

        executionZoneInstance.properties = params
        executionZoneInstance.enableExposedProcessingParameters = (params.enableExposedProcessingParameters != null)
        ControllerUtils.synchronizeProcessingParameters(ControllerUtils.getProcessingParameters(params), executionZoneInstance)

        if (!executionZoneInstance.save(flush: true)) {
            flash.action = 'update'
            render(view: "show", model: [executionZoneInstance: executionZoneInstance])
            return
        }

        flash.action = 'update'
        flash.message = message(code: 'default.updated.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), executionZoneInstance.id])
        redirect(action: "show", id: executionZoneInstance.id)
    }

    def delete() {
        def executionZoneInstance = ExecutionZone.get(params.execId)
        if (!executionZoneInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.execId])
            redirect(action: "list")
            return
        }

        try {
            executionZoneInstance.enabled = Boolean.FALSE
            executionZoneInstance.save(flush: true)
            flash.message = message(code: 'default.deleted.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.execId])
            redirect(action: "list")
        }
        catch (DataIntegrityViolationException e) {
            flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'executionZone.label', default: 'ExecutionZone'), params.execId])
            redirect(action: "show", id: params.execId)
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.applicationEventPublisher = eventPublisher
    }
}

class ExecuteExecutionZoneCommand extends AbstractExecutionZoneCommand {

    @Override
    ExecutionZoneAction getExecutionZoneAction() {
        return executionZoneService.createExecutionZoneAction(ExecutionZone.get(this.execId), this.scriptDir, this.execZoneParameters)
    }

}

class ExposeExecutionZoneCommand extends AbstractExecutionZoneCommand {

    @Override
    ExposedExecutionZoneAction getExecutionZoneAction() {
        ExposedExecutionZoneAction expAction = new ExposedExecutionZoneAction(executionZone: ExecutionZone.get(this.execId), scriptDir: this.scriptDir)
        ControllerUtils.synchronizeProcessingParameterValues(this.execZoneParameters, expAction)
        return expAction
    }

}

class GetExecutionZoneParametersCommand {

    def executionZoneService

    Long execId
    File scriptDir

    static constraints = {
        execId nullable:false
        scriptDir nullable:false, validator: { value, commandObj ->
            if (!value.exists()) {
                return "executionZone.failure.scriptDirNotExist"
            }
        }
    }

    def getExecutionZoneParameters() {
        def execZnParams = this.executionZoneService.getExecutionZoneParameters(ExecutionZone.get(this.execId), this.scriptDir).asType(ParameterMetadata[])
        execZnParams.sort(true, new MetadataParameterComparator())
    }
}

class GetScriptletBatchFlow {

    def executionZoneService

    File scriptDir

    static constraints = {
        scriptDir nullable:false, validator: { value, commandObj ->
            if (!value.exists()) {
                return "executionZone.failure.scriptDirNotExist"
            }
        }
    }

    ScriptletBatchFlow getScriptletBatchFlow() {
        this.executionZoneService.getScriptletBatchFlow(this.scriptDir)
    }
}

class GetReadmeCommand {

    private static final String README_FILENAME = "readme.md"

    File scriptDir
    String editorId

    protected File readme

    static constraints = {
        editorId nullable:false, blank: false
        scriptDir nullable:false, validator: { value, commandObj ->
            if (!value.exists()) {
                return "executionZone.failure.scriptDirNotExist"
            }
        }
    }

    private File getReadmeFile() {
        if (this.readme) {
            return this.readme
        }
        this.readme = new File("${this.scriptDir.path}${System.properties['file.separator']}${README_FILENAME}")
        if (!this.readme.exists()) {
            this.readme.createNewFile()
            this.readme.write("<!-- TODO: please comment me! -->")
        }
        return this.readme
    }

    String getReadmeMarkdown() {
        File readme = this.getReadmeFile()
        return readme.getText()
    }

    String getReadmeChecksum() {
        return this.getReadmeMarkdown().encodeAsMD5()
    }
}

class UpdateReadmeCommand extends GetReadmeCommand {

    String markdown
    String checksum

    @Override
    boolean validate() {
        if (!this.markdown || this.markdown.empty) {
            this.errors.reject('executionZone.failure.markdownEmpty', null, 'Markdown is empty')
        }
        if (!this.checksum || this.checksum.empty) {
            this.errors.reject('executionZone.failure.checksumEmpty', null, 'Checksum is empty')
        }
        if (this.checksum != this.getReadmeFile().text.encodeAsMD5()) {
            this.errors.reject('executionZone.readme.conflict', null, 'Readme was changed by another user')
        }
        return this.errors.hasErrors()
    }

    void updateReadme() {
        File readmeFile = this.getReadmeFile()
        readmeFile.write(this.markdown)
    }

}
