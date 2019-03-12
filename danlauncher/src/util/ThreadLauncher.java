/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.JTextArea;
import javax.swing.Timer;

/**
 *
 * @author dmcd2356
 */
public class ThreadLauncher {

    public interface ThreadAction {
        public void jobprestart (ThreadInfo threadInfo);
        public void jobstarted (ThreadInfo threadInfo);
        public void jobfinished (ThreadInfo threadInfo);
        public void allcompleted (ThreadInfo threadInfo);
    }

    // the data passed to the thread
    public static class ThreadInfo {
        private final String[] command;    // the command to execute
        private final String   workingdir; // the dir to execute the command from

        public int        exitcode;   // the exitcode returned from the job
        public Long       pid;        // the PID for the job
        public int        jobid;      // the job id
        public String     jobname;    // type of job being run
        public String     fname;      // filename being processed
        public String     signal;     // terminating signal (if any)
        public JTextArea  stdout;     // text widget to output stdout & stderr to
        
        ThreadInfo (String[] command, String workdir, JTextArea  stdout, String jobname, String fname) {
            this.command   = command;
            this.workingdir = workdir;
            this.stdout    = stdout;
            this.jobid     = ++jobnumber;
            this.jobname   = jobname;
            this.fname     = (fname == null) ? "" : fname;
            this.exitcode  = -1;
            this.signal    = "";
            this.pid       = -1L;
        }
    }

    // Queue for commands to execute in separate thread
    private RunnerThread runner;
    private ThreadAction action;
    private JTextArea    outTextArea;
    private final Queue<ThreadInfo> commandQueue = new LinkedList<>();
    private final Timer timer;
    private static int jobnumber;
    
    public ThreadLauncher (JTextArea  stdout) {
        this.action = null; // init to no action when thread completes
        this.outTextArea = stdout;

        // start the timer to run every 100 msec with no delay
        this.timer = new Timer(100, new TimerListener());
        this.timer.setInitialDelay(0);
    }
    
    public void init (ThreadAction action) {
        ThreadLauncher.jobnumber = 0;
        
        this.action = action; // specify the thread complete action
        this.runner = null;
        this.commandQueue.clear();
    }

//    public int launch (String[] command, String workdir, String jobname) {
//        return launch (command, workdir, jobname, "");
//    }

    public int launch (String[] command, String workdir, String jobname, String fname) {
        
        // if an output text area was not supplied, create one to capture the output
        // create an output area and add the command to the queue
        if (this.outTextArea == null)
            this.outTextArea = new JTextArea();

        // add the command to the queue
        ThreadInfo commandInfo = new ThreadInfo(command, workdir, this.outTextArea, jobname, fname);
        this.commandQueue.add(commandInfo);

        // make sure the timer is running
        this.timer.start(); 
        
        // return the current queue size
        return commandQueue.size();
    }

    public void sendStdin(String message) {
      if (this.runner != null) {
        OutputStream stdin = this.runner.getStdin();
        if (stdin != null) {
          BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
          try {
            System.out.println("Sending message: " + message);
            writer.write(message);
            writer.flush();
            writer.close();
          } catch (IOException ex) {
            System.err.println("ERROR: Failure in sendStdin for: " + message);
          }
        }
      }
    }
    
    public ThreadInfo stopAll () {
        if (this.runner == null)
            return null;
        
        ThreadInfo threadInfo = this.runner.getJobInfo();
        System.out.println("Trying to kill RunnerThread: " + Arrays.deepToString(threadInfo.command));
        this.runner.killProcess();
        this.commandQueue.clear();
        return threadInfo;
    }
    
    private class TimerListener implements ActionListener {
        
        private ThreadInfo threadInfo;
        
        @Override
        public void actionPerformed(ActionEvent event) {

            // no thread is running, start the first command (if there is any)
            if (runner == null) {
        	if (!commandQueue.isEmpty()) {
                    // get the first job to run
                    threadInfo = commandQueue.remove();
                    
                    // start the job
                    runner = new RunnerThread(threadInfo, threadInfo.command, threadInfo.workingdir, threadInfo.stdout);
                    Thread t = new Thread(runner);
                    t.start();
                    threadInfo.pid = runner.getJobPid();
                    if (action != null)
                        action.jobprestart(threadInfo);
        	}
                return;
            }

            // check if pid of process is asserted - this is when process is actually running
            long pid = runner.getJobPid();
            if (threadInfo.pid == -1 && pid >= 0) {
                threadInfo.pid = pid;
                action.jobstarted(threadInfo);
            }
            
            // if command still running, exit and wait till next timeout
            int exitcode = runner.getExitCode();
            if (runner.isRunning() || exitcode < 0) {
                return;
            }
            
            // command has completed - save the status
            threadInfo.exitcode = exitcode;
            
            // if there are more commands in the queue and last command was successful,
            // start the next command
            if (!commandQueue.isEmpty() && exitcode == 0) {
                if (action != null)
                    action.jobfinished(threadInfo);

                // get the next job to run
                threadInfo = commandQueue.remove();

                // start the job
                runner = new RunnerThread(threadInfo, threadInfo.command, threadInfo.workingdir, threadInfo.stdout);
                Thread t = new Thread(runner);
                t.start();
                threadInfo.pid = runner.getJobPid();
                if (action != null)
                    action.jobprestart(threadInfo);
            }
            else {
                // all commands have completed or there was an error on the last job...
                // stop timer
                Timer t = (Timer) event.getSource();
                t.stop();
            		
                // display potential errors
                String signal = "";
                switch (exitcode) {
                  // error codes when terminated by signals are 128 + signal value
                  case 129:   signal = "SIGHUP";   break;
                  case 130:   signal = "SIGINT";   break;
                  case 131:   signal = "SIGQUIT";  break;
                  case 134:   signal = "SIGABRT";  break;
                  case 137:   signal = "SIGKILL";  break;
                  case 139:   signal = "SIGSEGV";  break;
                  case 141:   signal = "SIGPIPE";  break;
                  case 143:   signal = "SIGTERM";  break;
                  default:
                    break;
                }

                if (!signal.isEmpty()) {
                  threadInfo.signal = signal;
                  System.out.println(threadInfo.signal + " on pid " + threadInfo.pid);
                } else if (exitcode == 0) {
                  System.out.println("All tasks finished.");
                } else {
                  System.err.println("ERROR: Failure executing command for pid " + threadInfo.pid
                      + ": exitcode = " + exitcode);
                }
                
                // empty queue
                runner = null;
                commandQueue.clear();

                // perform thread complete action
                if (action != null && threadInfo != null) {
                    action.jobfinished(threadInfo);
                    action.allcompleted(threadInfo);
                }
            }
        }
    }
}
