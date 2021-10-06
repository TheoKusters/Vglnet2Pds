package web;

import java.io.IOException;
import java.math.*;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import com.ibm.as400.access.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.ibm.as400.access.KeyedFile.KEY_EQ;

class StatusWorker {

    private final SequentialFile pfnBL;
    private final KeyedFile pfnEI;
    private final KeyedFile pfnRC;
    private final AS400 as400;
    private final Record pfnblr;
    private String memberCurr = "";
    private final DateFormat hhmmssDateFormat;
    private int errors;
    private final DateFormat timeStampFormat;
    private final Log logger = LogFactory.getLog(getClass());
    private static final String ALLOC_CMD = "ALCOBJ OBJ((NCL300CL *DTAARA *EXCL)) WAIT(0)";
    private static final String ALLOC_CMD_FAILURE_MSG = "CPF1002 - Cannot allocate object NCL300CL";

    private static final String DEALLOC_CMD = "DLCOBJ OBJ((NCL300CL *DTAARA *EXCL))";
    private static final String DEALLOC_FAILURE_MSG = "??";

    private static final String USERNAME = "";
    private static final String PASSWORD = "";

    public void verifyStartCondition() throws CouldNotAcquireLockException {
        try {
            CommandCall alloc = new CommandCall(as400);
            alloc.run(ALLOC_CMD);
            AS400Message[] messageList = alloc.getMessageList();

            for (int i = 0; i < messageList.length; i++) {
                logger.info("Response message received from ALLOC LOCK: " + messageList[i].getText());
                if (messageList[i].getText().contains(ALLOC_CMD_FAILURE_MSG)) {
                    logger.info("Shutting down, could not verify start condition on NCL300CL. ");
                    throw new CouldNotAcquireLockException("Shutting down, could not acquire lock on NCL300CL ");

                }
            }
            //no failure, so we hold the lock.
            CommandCall dealloc = new CommandCall(as400);
            dealloc.run(DEALLOC_CMD);
            AS400Message[] messages = dealloc.getMessageList();
            for (int i = 0; i < messageList.length; i++) {
                logger.info("Response message received from DEALLOC LOCK: " + i + ": " + messages[i].getText());
            }
        }catch(AS400SecurityException ex){
            logger.error("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
            throw new CouldNotAcquireLockException("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
        }catch(ErrorCompletingRequestException ex){
            logger.error("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
            throw new CouldNotAcquireLockException("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
        }catch(IOException ex){
            logger.error("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
            throw new CouldNotAcquireLockException("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
        }catch(InterruptedException ex){
            logger.error("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
            throw new CouldNotAcquireLockException("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
        }catch(java.beans.PropertyVetoException ex){
            logger.error("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
            throw new CouldNotAcquireLockException("Unable to verify start conditions; " + ex.getMessage() + ":" +
                    Arrays.toString(ex.getStackTrace()));
        }
    }



    StatusWorker() throws CouldNotAcquireLockException {

        hhmmssDateFormat = new SimpleDateFormat("HHmmss");
        timeStampFormat = new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss.SSS");
        as400 = new AS400();

        //we contiue iff we can retrieve a file lock.
        verifyStartCondition();

        QSYSObjectPathName fileName = new QSYSObjectPathName("*LIBL", "PFNBL", "FILE");
        pfnBL = new SequentialFile(as400, fileName.getPath());
        pfnEI = new KeyedFile();
        pfnRC = new KeyedFile();
        try {
            pfnBL.setRecordFormat();
            pfnBL.open(AS400File.WRITE_ONLY, 1, AS400File.COMMIT_LOCK_LEVEL_NONE);

            pfnEI.setSystem(as400);

            pfnRC.setSystem(as400);
            QSYSObjectPathName pfnrcPathName = new QSYSObjectPathName("*LIBL", "PFNRC", "FILE");
            pfnRC.setPath(pfnrcPathName.getPath());
            pfnRC.setRecordFormat();
            pfnRC.open(AS400File.READ_ONLY, 1, AS400File.COMMIT_LOCK_LEVEL_NONE);
            if (!pfnRC.isOpen()) {
                logger.debug("PFNRC could not be opened");
            }


        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        pfnblr = new Record(pfnBL.getRecordFormat());

    }


    synchronized void updatePFNEI(String member, int id, int status, int batchsize) {

        try {

            if (!memberCurr.equals(member)) {
                if (!memberCurr.equals("")) {
                    pfnEI.close();
                }
                QSYSObjectPathName pfneiPathName = new QSYSObjectPathName("*LIBL", "PFNEI", member, "MBR");
                pfnEI.setPath(pfneiPathName.getPath());
                memberCurr = member;
                pfnEI.setRecordFormat();
                pfnEI.open(AS400File.READ_WRITE, 1, AS400File.COMMIT_LOCK_LEVEL_NONE);
            }

            // Read the first record of the file.
            Object[] key = new Object[1];
            key[0] = new BigDecimal(id);
            Record record = pfnEI.read(key);

            if (record != null) {
                record.setField("EISTAT", new BigDecimal(status));
                record.setField("EIBATC", new BigDecimal(batchsize));
                if (status == 99) {
                    record.setField("EITCOM", new BigDecimal(Integer.parseInt(hhmmssDateFormat.format(new Date()))));
                }
                pfnEI.update(record);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    synchronized boolean StopRequested(String Elok) {

        boolean iStp = false;
        String rcIstp;
        String JobName;
        JobName = "EXPWS" + Elok.trim();

        try {
            // Read the first record of the file.
            Object[] rckey = new Object[1];
            rckey[0] = JobName;
            Record rcrec = pfnRC.read(rckey, 1);

            if (rcrec != null) {
                rcIstp = rcrec.getField("RCISTP").toString();
                if (!rcIstp.equals("2")) {
                    logger.warn("Stop requested for job: " + JobName);
                    iStp = true;
                }
            } else {
                iStp = true;
                logger.warn("Jobname: " + JobName + " not found in PFNRC");
            }
        } catch (AS400Exception e) {
            e.printStackTrace();
        } catch (AS400SecurityException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return iStp;
    }

    synchronized void writePFNBL(
            String member,
            int[] ids,
            int batchSize,
            int batchLength,
            int httpStatus,
            String httpStatusText,
            byte[] jsonStart,
            Timestamp httpStart,
            Timestamp httpEnd,
            byte[] wwElok,
            int timeJson) {

        String jsonStartStr = new String(jsonStart);
        Timestamp jsonTS;
        try {
            jsonTS = new Timestamp(timeStampFormat.parse(jsonStartStr).getTime());
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            jsonTS = new Timestamp(new Date().getTime());
        }


        pfnblr.setField("BLELOK", new String(wwElok).trim());
        pfnblr.setField("BLBAID", member.trim() + "-" + ids[0] + "-" + ids[batchSize - 1]);
        pfnblr.setField("BLBASI", new BigDecimal(batchSize));
        pfnblr.setField("BLBALE", new BigDecimal(batchLength));
        pfnblr.setField("BLTIMJ", new BigDecimal(timeJson));
        pfnblr.setField("BLTIMH", new BigDecimal(httpEnd.getTime() - httpStart.getTime()));
        pfnblr.setField("BLRTNC", new BigDecimal(httpStatus));
        if (httpStatusText.length() > 50) {
            httpStatusText = httpStatusText.substring(0, 50);
        }
        pfnblr.setField("BLRTNT", httpStatusText);
        pfnblr.setField("BLSTJC", jsonTS.toString().replaceAll(" ", "-").replaceAll(":", "."));
        pfnblr.setField("BLSTHT", httpStart.toString().replaceAll(" ", "-").replaceAll(":", "."));
        pfnblr.setField("BLENHT", httpEnd.toString().replaceAll(" ", "-").replaceAll(":", "."));
        try {
            pfnBL.write(pfnblr);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String messageData = String.format("%250s%3s%100s%-10s",
                "",
                new String(wwElok),
                String.format("%-100s", httpStatusText).substring(0, 100),
                httpStatus);
        if (httpStatus != 200) {
            errors += 1;
            if (errors == 1) {
                MessageQueue queue = new MessageQueue(as400, "/qsys.lib/qsysopr.msgq");
                try {
                    queue.sendInformational("ERR7056", new QSYSObjectPathName("*LIBL", "NMSGF", "MSGF").getPath(),
                            messageData.getBytes("Cp037"));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

}