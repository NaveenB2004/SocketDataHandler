package com.naveenb2004;

import lombok.Cleanup;
import lombok.NonNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataProcessor {
    @NonNull
    protected static byte[] serialize(@NonNull DataHandler dataHandler) throws IOException {
        ByteArrayOutputStream out;

        dataHandler.setTimestamp(DataHandler.timestamp());

        StringBuilder header = new StringBuilder("{");
        header.append(dataHandler.getRequest().getBytes(StandardCharsets.UTF_8).length).append(",");
        header.append(dataHandler.getDataType().getValue()).append(",");

        String suffix = "";
        if (dataHandler.getDataType() == DataHandler.DataType.OBJECT) {
            out = new ByteArrayOutputStream();
            @Cleanup
            ObjectOutputStream oOut = new ObjectOutputStream(out);
            oOut.writeObject(dataHandler.getData());
            oOut.flush();
            long length = out.toByteArray().length;
            header.append(length);
            dataHandler.setTotalDataSize(length);
        } else if (dataHandler.getDataType() == DataHandler.DataType.FILE) {
            File file = dataHandler.getFile();
            suffix = file.getName().substring(file.getName().lastIndexOf(".") + 1) + "$";
            long length = file.length();
            header.append(length);
            dataHandler.setTotalDataSize(length);
        } else {
            header.append("0");
            dataHandler.setTotalDataSize(0L);
        }
        header.append("}");
        header.append(dataHandler.getRequest()).append(dataHandler.getTimestamp());
        header.append(suffix);

        out = new ByteArrayOutputStream();
        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    protected void deserialize(@NonNull SocketDataHandler socketDataHandler)
            throws IOException, ClassNotFoundException {
        InputStream in = socketDataHandler.getSOCKET().getInputStream();
        ByteArrayOutputStream out;
        byte[] buff;
        while (true) {
            if (in.read() == '{') {
                long defaultBufferSize = SocketDataHandler.defaultBufferSize;
                File tempFolder = SocketDataHandler.tempFolder;

                out = new ByteArrayOutputStream();
                buff = new byte[1];
                while (true) {
                    in.read(buff);

                    if (new String(buff, StandardCharsets.UTF_8).equals("}")) {
                        break;
                    }

                    out.write(buff);
                }

                String[] headers = out.toString(StandardCharsets.UTF_8).split(",");
                int requestLen = Integer.parseInt(headers[0]);
                int timestampLen = 13;
                String dataTypeVal = headers[1];
                long dataLen = Long.parseLong(headers[2]);

                buff = new byte[requestLen];
                in.read(buff);
                String request = new String(buff, StandardCharsets.UTF_8);

                buff = new byte[timestampLen];
                in.read(buff);
                long timestamp = Long.parseLong(new String(buff, StandardCharsets.UTF_8));

                DataHandler dh;
                DataHandler.DataType dataType = DataHandler.DataType.getType(dataTypeVal);
                out = new ByteArrayOutputStream();

                if (dataType == DataHandler.DataType.OBJECT) {
                    buff = new byte[Math.toIntExact(defaultBufferSize)];
                    while (true) {
                        if (dataLen <= defaultBufferSize) {
                            buff = new byte[Math.toIntExact(dataLen)];
                            in.read(buff);
                            out.write(buff);
                            break;
                        } else {
                            in.read(buff);
                            out.write(buff);
                        }
                        dataLen -= defaultBufferSize;
                    }
                    @Cleanup
                    ByteArrayInputStream bais = new ByteArrayInputStream(out.toByteArray());
                    @Cleanup
                    ObjectInputStream ois = new ObjectInputStream(bais);

                    dh = new DataHandler(request, (Serializable) ois.readObject());
                } else if (dataType == DataHandler.DataType.FILE) {
                    buff = new byte[1];
                    while (true) {
                        in.read(buff);

                        if (new String(buff, StandardCharsets.UTF_8).equals("$")) {
                            break;
                        }

                        out.write(buff);
                    }

                    if (!tempFolder.exists()) {
                        tempFolder.mkdirs();
                    }
                    Path tempFile = Files.createTempFile(tempFolder.toPath(), null,
                            "." + out.toString(StandardCharsets.UTF_8));
                    FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    buff = new byte[Math.toIntExact(defaultBufferSize)];
                    while (true) {
                        if (dataLen <= defaultBufferSize) {
                            buff = new byte[Math.toIntExact(dataLen)];
                            in.read(buff);
                            bos.write(buff);
                            break;
                        } else {
                            in.read(buff);
                            bos.write(buff);
                        }
                        dataLen -= defaultBufferSize;
                    }
                    bos.close();
                    fos.close();
                    dh = new DataHandler(request, tempFile.toFile());
                } else {
                    dh = new DataHandler(request);
                }

                dh.setDataType(dataType);
                dh.setTimestamp(timestamp);

                socketDataHandler.receive(dh);
            }
        }
    }
}
