/*
 * Copyright 2024 Naveen Balasooriya
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.naveenb2004.SocketDataHandler;

import com.naveenb2004.SocketDataHandler.DataHandler.DataType;
import com.naveenb2004.SocketDataHandler.PreUpdateHandler.PreDataHandler;
import com.naveenb2004.SocketDataHandler.PreUpdateHandler.PreUpdateHandler;
import lombok.*;

import java.io.*;
import java.net.Socket;

public abstract class SocketDataHandler implements Closeable {
    @Getter
    @NonNull
    private final Socket SOCKET;
    @Getter
    private static long defaultBufferSize = 1024L;
    @Getter
    @Setter
    @NonNull
    private static File tempFolder = new File("Temp");
    @Getter
    private final PreUpdateHandler PRE_UPDATE_HANDLER = new PreUpdateHandler();
    public String id;

    public SocketDataHandler(@NonNull Socket SOCKET) {
        this.SOCKET = SOCKET;
        Thread DATA_PROCESSOR = new DataProcessor(this);
        DATA_PROCESSOR.setName("SocketDataHandler - DataProcessor");
        DATA_PROCESSOR.start();
    }

    public static void setDefaultBufferSize(long defaultBufferSize) {
        if (defaultBufferSize <= 0) {
            throw new IllegalArgumentException("Default buffer size must be greater than 0 bytes!");
        }
        SocketDataHandler.defaultBufferSize = defaultBufferSize;
    }

    @Synchronized
    public void send(@NonNull DataHandler dataHandler) throws IOException {
        OutputStream os = SOCKET.getOutputStream();
        byte[] buffer = new byte[Math.toIntExact(defaultBufferSize)];
        PreDataHandler preDataHandler = null;
        if (dataHandler.getDataType() != DataType.NONE) {
            preDataHandler = new PreDataHandler(PRE_UPDATE_HANDLER, dataHandler.getUUID(),
                    dataHandler.getRequest(), PreDataHandler.Method.SEND, dataHandler.getDataType());
        }
        byte[] data = DataProcessor.serialize(dataHandler, preDataHandler);

        os.write(data);
        os.flush();

        if (dataHandler.getDataType() != DataType.NONE) {
            FileInputStream fin = null;
            BufferedInputStream bos;
            if (dataHandler.getDataType() == DataType.FILE) {
                fin = new FileInputStream(dataHandler.getFile());
                bos = new BufferedInputStream(fin);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                @Cleanup
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(dataHandler.getData());
                oos.flush();
                bos = new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()));
            }

            int c;
            int i = 0;
            while ((c = bos.read(buffer)) != -1) {
                os.write(buffer, 0, c);
                i += c;
                preDataHandler.setTransferredDataSize(i);
            }
            os.flush();
            bos.close();

            if (fin != null) {
                fin.close();
            }
            preDataHandler.setCompleted(true);
        }
    }

    public abstract void onUpdateReceived(@NonNull DataHandler update);

    public abstract void onDisconnected(@NonNull DataProcessor.DisconnectStatus status, Exception exception);

    public void close() throws IOException {
        if (!SOCKET.isClosed()) {
            SOCKET.close();
        }
    }
}
