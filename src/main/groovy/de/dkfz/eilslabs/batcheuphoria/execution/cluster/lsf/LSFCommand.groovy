/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.eilslabs.batcheuphoria.execution.cluster.lsf

import de.dkfz.eilslabs.batcheuphoria.execution.cluster.pbs.PBSResourceProcessingCommand
import de.dkfz.eilslabs.batcheuphoria.jobs.Command
import de.dkfz.eilslabs.batcheuphoria.jobs.Job
import de.dkfz.eilslabs.batcheuphoria.jobs.ProcessingCommands
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.tools.LoggerWrapper

import static de.dkfz.roddy.StringConstants.COLON
import static de.dkfz.roddy.StringConstants.EMPTY


/**
 * This class is used to create and execute bsub commands
 *
 * Created by kaercher on 12.04.17.
 */
@groovy.transform.CompileStatic
class LSFCommand extends Command {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(LSFCommand.class.name)

    public static final String PARM_LOGPATH = " -o "
    public static final String BSUB = "bsub"
    public static final String PARM_DEPENDS = " -W depend="
    public static final String PARM_MAIL = " -u "
    public static final String PARM_VARIABLES = " -env "
    public static final String PARM_JOBNAME = " -J "
    public static final String PARM_GROUPLIST = " -G"

    /**
     * The bsub log directoy where all output is put
     */
    protected File loggingDirectory

    /**
     * The command which should be called
     */
    protected String command

    protected List<String> dependencyIDs


    protected final List<ProcessingCommands> processingCommands

    /**
     *
     * @param id
     * @param parameters
     * @param arrayIndices
     * @param command
     * @param filesToCheck
     */
    LSFCommand(LSFJobManager parentManager, Job job, String id, List<ProcessingCommands> processingCommands, Map<String, String> parameters, Map<String, Object> tags, List<String> arrayIndices, List<String> dependencyIDs, String command, File loggingDirectory) {
        super(parentManager, job, id, parameters, tags)
        this.processingCommands = processingCommands
        this.command = command
        this.loggingDirectory = loggingDirectory
        //this.arrayIndices = arrayIndices ?: new LinkedList<String>()
        this.dependencyIDs = dependencyIDs ?: new LinkedList<String>()
    }


    String getEmailParameter(String address) {

        return PARM_MAIL + address

    }


    String getGroupListString(String groupList) {

        return PARM_GROUPLIST + groupList

    }


    String getVariablesParameter() {

        return PARM_VARIABLES

    }

    protected String getDependencyOptionSeparator() {
        return COLON
    }

    protected String getDependencyIDSeparator() {
        return COLON
    }


    protected String getAdditionalCommandParameters() {

        return ""

    }


    protected String getDependsSuperParameter() {

        PARM_DEPENDS

    }

    @Override
    String toString() {

        String email = parentJobManager.getUserEmail()
        String umask = parentJobManager.getUserMask()
        String groupList = parentJobManager.getUserGroup()
        String accountName = parentJobManager.getUserAccount()
        boolean useParameterFile = parentJobManager.isParameterFileEnabled()
        boolean holdJobsOnStart = parentJobManager.isHoldJobsEnabled()

        StringBuilder bsubCall = new StringBuilder(EMPTY)

        bsubCall << BSUB << " -R 'select[type==any]'" << PARM_JOBNAME << id

        if (holdJobsOnStart) bsubCall << " -H "

        bsubCall << getAdditionalCommandParameters()

        if (loggingDirectory) bsubCall << PARM_LOGPATH << loggingDirectory

        if (email) bsubCall << getEmailParameter(email)

        if (groupList && groupList != "UNDEFINED") bsubCall << getGroupListString(groupList)

        bsubCall << assembleProcessingCommands()

        bsubCall << prepareParentJobs((List<Job>) job.getParentJobs())

        bsubCall << assembleVariableExportString()

        bsubCall << " " << prepareToolScript(job)

        return bsubCall
    }


    StringBuilder assembleProcessingCommands() {
        StringBuilder bsubCall = new StringBuilder()
        for (ProcessingCommands pcmd in job.getListOfProcessingCommand()) {
            if (!(pcmd instanceof PBSResourceProcessingCommand)) continue
            PBSResourceProcessingCommand command = (PBSResourceProcessingCommand) pcmd
            if (command == null)
                continue
            bsubCall << StringConstants.WHITESPACE << command.getProcessingString()
        }
        return bsubCall
    }


    StringBuilder assembleVariableExportString() {

        StringBuilder envParams = new StringBuilder()

        job.parameters.eachWithIndex { key, value, index ->
            if (index == 0)
                envParams << getVariablesParameter() << " \" ${key}='${value}'"
            else
                envParams << "," + "${key}='${value}'"
        }

        if (envParams.length() > 0)
            envParams << "\""

        return envParams
    }

    /**
     * Prepare parent jobs is part of @prepareExtraParams
     * @param jobs
     * @return part of parameter area
     */
    private String prepareParentJobs(List<Job> jobs) {
        if (jobs) {
            String joinedParentJobs = jobs.collect { "done\\(${it.getJobID()}\\)" }.join(" &amp\\;&amp\\; ")
            if (joinedParentJobs.length() > 0)
                return " -w \"${joinedParentJobs} \""
        }

        return ""
    }


    private String prepareToolScript(Job job) {
        String toolScript
        if (job.getToolScript() != null && job.getToolScript().length() > 0) {
            toolScript = job.getToolScript()
        } else {
            if (job.getTool() != null) toolScript = job.getTool().getAbsolutePath()
        }
        if (toolScript) {
            return toolScript
        } else {
            return ""
        }
    }

}