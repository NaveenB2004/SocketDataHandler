package Server;

import Common.SampleObject;
import com.naveenb2004.DataHandler;
import com.naveenb2004.PreUpdateHandler.PreDataHandler;
import com.naveenb2004.PreUpdateHandler.PreUpdateHandler;
import com.naveenb2004.PreUpdateHandler.PreUpdateWatcher;
import com.naveenb2004.SocketDataHandler;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.awt.*;
import java.io.IOException;
import java.net.Socket;

public class ServerHelper extends SocketDataHandler implements PreUpdateWatcher {

    public ServerHelper(@NonNull final Socket SOCKET) {
        super(SOCKET);
        PreUpdateHandler preUpdateHandler = getPreUpdateHandler();
        preUpdateHandler.addWatcher(this);
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(@NonNull DataHandler update) {
        System.out.println("Server : Update received!");
        System.out.println("Server : Request = " + update.getRequest());
        System.out.println("Server : Timestamp = " + update.getTimestamp());
        System.out.println("Server : DataType = " + update.getDataType());

        if (update.getDataType() == DataHandler.DataType.FILE) {
            try {
                Desktop.getDesktop().open(update.getFile());
                System.out.println("Server : File opened!");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (update.getDataType() == DataHandler.DataType.OBJECT) {
            SampleObject object = (SampleObject) update.getData();
            System.out.println("Server : Object Details = " + object.toString());
        }

        if (update.getRequest().equals("/closeConnection")) {
            close();
            System.out.println("Server : Connection closed!");
        }

        System.out.println();
    }

    @Override
    public void onPreUpdateSeen(@NonNull PreDataHandler preUpdate) {

    }
}
