/*
 * Copyright 2012 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.kie.services.impl.bpmn2;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.jbpm.kie.services.impl.model.ProcessAssetDesc;
import org.jbpm.process.instance.StartProcessHelper;
import org.jbpm.services.api.model.UserTaskDefinition;
import org.kie.api.definition.process.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a package level class that is used by different BPMN2 handlers ( in this package) to store information
 * about a BPMN2 process.
 */
public class ProcessDescRepoHelper {

    private static final Logger logger = LoggerFactory.getLogger(ProcessDescRepoHelper.class);
            
    private ProcessAssetDesc process;
    private Map<String, UserTaskDefinition> tasks = new HashMap<String, UserTaskDefinition>();
    private Map<String, Map<String, String>> taskInputMappings = new HashMap<String, Map<String, String>>();
    private Map<String, Map<String, String>> taskOutputMappings = new HashMap<String, Map<String, String>>();
    private Map<String, String> inputs = new HashMap<String, String>();
    private Map<String, Collection<String>> taskAssignments = new HashMap<String, Collection<String>>();
    private Map<String, String> itemDefinitions = new HashMap<String, String>();
    private Map<String, String> serviceTasks = new HashMap<String, String>();
    private Map<String, String> globalItemDefinitions = new HashMap<String, String>();
 
    private Collection<String> reusableSubProcesses = new HashSet<String>(1);
    private Set<String> referencedClasses = new HashSet<String>(1);
    private Set<String> unqualifiedClasses = new HashSet<String>(1);
    private Set<String> referencedRules = new HashSet<String>(1);
    
    private Queue<String> unresolvedReusableSubProcessNames = new ArrayDeque<String>();
    
    public ProcessDescRepoHelper() {
    }

    public void setProcess(ProcessAssetDesc process) {
        this.process = process;
    }

    public boolean hasUnresolvedReusableSubProcessNames() { 
       return ! unresolvedReusableSubProcessNames.isEmpty(); 
    }
    
    public void resolveReusableSubProcessNames( Collection<Process> deploymentProcesses ) {
        // build map of process name -> process id
        Map<String, Process> processNameProcessIdMap = new HashMap<String, Process>(deploymentProcesses.size());
        for( Process process : deploymentProcesses ) { 
            String processName = process.getName();
           Process previousProcess = processNameProcessIdMap.put(processName, process);
           if( previousProcess != null ) { 
               Comparator<Process> processComparator = StartProcessHelper.getComparator(processName);
               if( processComparator.compare(previousProcess, process) > 0 ) { 
                  processNameProcessIdMap.put(processName, previousProcess);
               }
           }
        }
        
        // resolve process names called in process
        synchronized(unresolvedReusableSubProcessNames) { 
            Iterator<String> iter = unresolvedReusableSubProcessNames.iterator();
            while( iter.hasNext() ) { 
                String processName  = iter.next();
                Process deploymentProcess = processNameProcessIdMap.get(processName);
                if( deploymentProcess == null ) { 
                    logger.error("Unable to resolve process name '{}' called in process '{}'", processName, getProcess().getId());
                } else { 
                    String processIdForProcessName = deploymentProcess.getId();
                    reusableSubProcesses.add(processIdForProcessName);
                    iter.remove();
                }
            }
        } 
    }

    
    public ProcessAssetDesc getProcess() {
        return process;
    }

    public Map<String, UserTaskDefinition> getTasks() {
        return tasks;
    }

    public Map<String, Map<String, String>> getTaskInputMappings() {
        return taskInputMappings;
    }
    
    public Map<String, Map<String, String>> getTaskOutputMappings() {
        return taskOutputMappings;
    }

    public Map<String, String> getInputs() {
        return inputs;
    }

    public Map<String, Collection<String>> getTaskAssignments() {
        return taskAssignments;
    }

    public Map<String, String> getItemDefinitions() {
        return itemDefinitions;
    }

    public Map<String, String> getServiceTasks() {
        return serviceTasks;
    }

    public Map<String, String> getGlobalItemDefinitions() {
        return globalItemDefinitions;
    }

    public Collection<String> getReusableSubProcesses() {
        return reusableSubProcesses;
    }

    public void addReusableSubProcessName(String processName) {
        synchronized(unresolvedReusableSubProcessNames) { 
            unresolvedReusableSubProcessNames.add(processName);
        }
    }
    
    public Set<String> getReferencedClasses() {
        return referencedClasses;
    }

    public Set<String> getUnqualifiedClasses() {
        return unqualifiedClasses;
    }
    
    public Set<String> getReferencedRules() {
        return referencedRules;
    }

    public void clear(){
        process = null;
        tasks.clear();
        taskInputMappings.clear();
        taskOutputMappings.clear();
        inputs.clear();
        taskAssignments.clear();
        reusableSubProcesses.clear();
        itemDefinitions.clear();
        serviceTasks.clear();
        globalItemDefinitions.clear();
        referencedClasses.clear();
        referencedRules.clear();
    }

    public void resolveUnqualifiedClasses() {
        Set<String> qualifiedClassSimpleNames = new HashSet<String>();
        for( String className : referencedClasses ) { 
            qualifiedClassSimpleNames.add(className.substring(className.lastIndexOf('.') + 1)); 
        }
        for( Iterator<String> iter = unqualifiedClasses.iterator(); iter.hasNext(); ) { 
            if( qualifiedClassSimpleNames.contains(iter.next()) ) { 
                iter.remove();
            }
        }
        for( Iterator<String> iter = unqualifiedClasses.iterator(); iter.hasNext(); ) { 
            String name = iter.next();
            if( "Object".equals(name) || "String".equals(name) 
                || "Float".equals(name) || "Integer".equals(name) 
                || "Boolean".equals(name) ) { 
               referencedClasses.add("java.lang." + name );
               iter.remove();
            }
        }
        for( String className : unqualifiedClasses ) { 
            logger.warn("Unable to resolve unqualified class name, adding to list of classes: '{}'", className );
            referencedClasses.add(className);
        }
    }

}
