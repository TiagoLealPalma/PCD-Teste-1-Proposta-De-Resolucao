import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Node {

    // ============================================ Dado Pelo Enunciado ================================================

    public class NodeAddress{
        private String address;
        private int port;

        public NodeAddress(String address, int port){
            this.address = address;
            this.port = port;
        }

        public String getAddress(){return address;}
        public int getPort(){return port;}
    }

    public List<NodeAddress> availableNodes;

    public Node(){
        // Para fins de teste (não era necessário meter no teste)
        availableNodes = new ArrayList<NodeAddress>();
        for (int i = 0; i < 5; i++) {
            availableNodes.add(new NodeAddress("127.0.0.1", 8080 + i));
        }
    }

    public class WordSearchMessage implements Serializable{
        public String word;
        public WordSearchMessage(String word){
            this.word = word;
        }
        public String getWord(){return word;}
    }

    private Object searchWordInFolder(WordSearchMessage wsr) {
        return new FileSearchResult(Math.random() > 0.5 ? 1 : 0);
    }

    //==================================================== Ex. 4 =======================================================
    public class RemoteNodeSearcher extends Thread{
        private NodeAddress address;
        private TimedCountDownLatch latch;
        private Node node;
        private String word;
        private List<NodeAddress> answeredAddresses;

        public RemoteNodeSearcher(NodeAddress address, TimedCountDownLatch latch, Node node, String word, List<NodeAddress> answeredAdresses){
            this.address = address;
            this.latch = latch;
            this.node = node;
            this.word = word;
            this.answeredAddresses = answeredAdresses;
        }

        public void run(){
            if(node.remoteNodeHasFileWithWord(address, word)){
                synchronized(answeredAddresses){answeredAddresses.add(address);}
            }
            latch.countDown(); // Fora porque dá countdown mesmo que a resposta seja negativa, só não se adiciona para
                                                            // lista comum porque os resultados da pesquisa foram falsos
        }
    }



    public List<NodeAddress> searchRemoteNodesForWords(String word){
        List<NodeAddress> answerAdresses = new ArrayList<>();
        TimedCountDownLatch latch = new TimedCountDownLatch(availableNodes.size());

        for (NodeAddress nodeAddress : availableNodes) {
            new RemoteNodeSearcher(nodeAddress, latch, this, word, answerAdresses).start();
        }
        try {
            if(!latch.await(1000)) System.err.println("Timeout waiting for remote node search, not all nodes have answered");
        } catch (InterruptedException e) {e.printStackTrace();}
        return answerAdresses;
    }

    // =================================================== Ex. 5 =======================================================
    public class TimedCountDownLatch{
        private int count;

        public TimedCountDownLatch(int count){this.count = count;}

        public synchronized boolean await(long timeout) throws InterruptedException {
            if(count != 0){
                wait(timeout);
            }
            return count == 0;
        }

        public synchronized void countDown(){
            this.count--;
            if(count == 0)
                notifyAll();
        }

    }

    //====================================================== Ex. 6 =====================================================
    public class FileSearchResult implements Serializable {
        private int fileCount;
        public FileSearchResult(int fileCount){
            this.fileCount = fileCount;
        }
        public int getFileCount(){return fileCount;}
    }

    private boolean remoteNodeHasFileWithWord(NodeAddress address, String word) {
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(address.getAddress());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        try(Socket socket = new Socket(inetAddress, address.getPort());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ){
            out.writeObject(new WordSearchMessage(word));

            Object message = in.readObject();

            if(message instanceof FileSearchResult fr){
                return fr.getFileCount() == 0;
            }


        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public void startServer(int port){
        new Thread(()->{
            try {
                ServerSocket ss = new ServerSocket(port);

                Socket clientSocket = ss.accept();
                new Thread(()-> handler(clientSocket)).start();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }).start();
    }

    private void handler(Socket clientSocket) {
        try(ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

            Object message = in.readObject();
            if(message instanceof WordSearchMessage wsr){
                out.writeObject(searchWordInFolder(wsr));
            }
            if(message instanceof FileSearchResult fr){

            }


        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
