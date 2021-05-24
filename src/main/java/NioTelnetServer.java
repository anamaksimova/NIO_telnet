import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class NioTelnetServer {
        public static final String LS_COMMAND = "\tls    view all files and directories"+ System.lineSeparator();
        public static final String MKDIR_COMMAND = "\tmkdir [dirname]    create directory"+ System.lineSeparator();
        public static final String CHANGE_NICKNAME = "\tnick    change nickname"+ System.lineSeparator();
        public static final String TOUCH = "\ttouch [filename]   create file"+ System.lineSeparator();
        public static final String RM_COMMAND = "\trm [dir/filename]   remove file|directory"+ System.lineSeparator();
        public static final String COPY = "\tcopy    copy [dir/filename] [new_dir/filename]"+ System.lineSeparator();
        public static final String CAT_COMMAND = "\tcat [dir/filename]    read file"+ System.lineSeparator();

        private final ByteBuffer buffer = ByteBuffer.allocate(512);

        public NioTelnetServer() throws IOException {
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(5678));
            server.configureBlocking(false);
            // OP_ACCEPT, OP_READ, OP_WRITE
            Selector selector = Selector.open();

            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server started");

            while (server.isOpen()) {
                selector.select();

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        handleAccept(key, selector);
                    } else if (key.isReadable()) {
                        handleRead(key, selector);
                    }
                    iterator.remove();
                }
            }
        }

        private void handleRead(SelectionKey key, Selector selector) throws IOException {
            SocketChannel channel = ((SocketChannel) key.channel());
            SocketAddress client = channel.getRemoteAddress();
            int readBytes = channel.read(buffer);
            if (readBytes < 0) {
                channel.close();
                return;
            } else if (readBytes == 0) {
                return;
            }

            buffer.flip();

            StringBuilder sb = new StringBuilder();
            while (buffer.hasRemaining()) {
                sb.append((char) buffer.get());
            }

            buffer.clear();

            // TODO
            // touch [filename] - создание файла
            // mkdir [dirname] - создание директории
            // cd [path] - перемещение по каталогу (.. | ~ )
            // rm [filename | dirname] - удаление файла или папки
            // copy [src] [target] - копирование файла или папки
            // cat [filename] - просмотр содержимого
            // вывод nickname в начале строки

            // NIO
            // NIO telnet server

            if (key.isValid()) {
                String command = sb
                        .toString()
                        .replace("\n", "")
                        .replace("\r", "");

                if ("--help".equals(command)) {
                    sendMessage(LS_COMMAND, selector, client);
                    sendMessage(MKDIR_COMMAND, selector, client);
                    sendMessage(CHANGE_NICKNAME, selector, client);
                    sendMessage(TOUCH, selector, client);
                    sendMessage(RM_COMMAND, selector, client);
                    sendMessage(COPY, selector, client);
                    sendMessage(CAT_COMMAND, selector, client);


                } else if ("ls".equals(command)) {
                    sendMessage(getFileList().concat("\n"), selector, client);

                } else if (command.startsWith("mkdir")){
                    String[] cmd= command.split(" ",2);
                    String dirname = cmd[1];
                   
                   makeDir(dirname);
                    sendMessage(("Директория создана: "+dirname+System.lineSeparator()), selector, client);
                    System.out.println("Directory was made: "+dirname + channel.getRemoteAddress());

                } else if (command.startsWith("touch")){
                    String[] cmd= command.split(" ",2);
                    String filename = cmd[1];

                    touch(filename);
                    sendMessage(("Файл создан: "+filename+ System.lineSeparator()), selector, client);
                    System.out.println("File was made: "+filename + channel.getRemoteAddress());

                }else if (command.startsWith("rm")){
                    String[] cmd= command.split(" ",2);
                    String dir_filename = cmd[1];
                    Path path = Paths.get(dir_filename);
                    if (Files.exists(path)){
                        Files.delete(path);
                        sendMessage(("Файл удален: "+dir_filename+System.lineSeparator()), selector, client);
                        System.out.println("File deleted: "+dir_filename + channel.getRemoteAddress());
                    } else {
                        sendMessage("Файл не найден. Возможно вы опять забыли ввести полный путь к файлу "+ dir_filename+System.lineSeparator(), selector, client);
                    }


                }else if (command.startsWith("cat")){
                    String[] cmd= command.split(" ", 2);
                    String dir_filename = cmd[1];
                    Path path = Paths.get(dir_filename);
                    byte[] text = Files.readAllBytes(path);
                    sendMessage("Содержимое файла: "+ dir_filename+System.lineSeparator(), selector, client);
                    for (byte t:text) {
                        sendMessage(String.valueOf((char) t), selector, client);
                    }

                    System.out.println("File read: "+dir_filename + channel.getRemoteAddress());

                } else if (command.startsWith("copy")){
                    String[] cmd= command.split(" ", 3);
                    String old_dir_filename = cmd[1];
                    String new_dir_filename = cmd[2];
                    Path pathOld = Paths.get(old_dir_filename);
                    Path pathNew = Paths.get(new_dir_filename);
                    if (Files.exists(pathOld)&&Files.exists(pathNew)){
                    Files.copy(pathOld, pathNew,
                            StandardCopyOption.REPLACE_EXISTING);
                    sendMessage("Содержимое файла "+ old_dir_filename+" скопировано в "+new_dir_filename+System.lineSeparator(), selector, client);
                    System.out.println("File copied: "+new_dir_filename + channel.getRemoteAddress());
                    } else   { sendMessage("Проверьте правильность написания запроса, заданных директорий не существует " +
                             pathNew+" "+pathOld+System.lineSeparator(), selector, client);
                    }

                }     else if ("exit".equals(command)) {
                    System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                    channel.close();
                    return;
                }
            }
        }

    private void touch(String filename) throws IOException {
        Path path = Paths.get("server/"+ filename);
        Files.createFile(path);
    }

    private void makeDir(String dirname) {
        Path path = Paths.get(dirname);
        try {
            Path newDir = Files.createDirectory(path);
        } catch(FileAlreadyExistsException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileList() {
            return String.join(" ", new File("server").list());
        }

        private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
            for (SelectionKey key : selector.keys()) {
                if (key.isValid() && key.channel() instanceof SocketChannel) {
                    if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
                        ((SocketChannel)key.channel())
                                .write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }

        private void handleAccept(SelectionKey key, Selector selector) throws IOException {
            SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
            channel.configureBlocking(false);
            System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

            channel.register(selector, SelectionKey.OP_READ, "some attach");
            channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
            channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
        }

        public static void main(String[] args) throws IOException {
            new NioTelnetServer();
        }
    }

