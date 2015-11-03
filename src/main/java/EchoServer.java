import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerRegistry;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

import com.google.common.base.Throwables;
import io.dropwizard.util.Duration;
import io.dropwizard.util.Size;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;

/**
 * Purpose of this class is to experiment with different netty options for the echo server.
 */
public class EchoServer
{
    public enum Engine
    {
        Blocking, NonBlocking, Epoll
    }

    @Option(name = "-buffer", usage = "Set the tx/rx queue buffer sizes for socket (default = 8MB).")
    private Size bufferSize = Size.megabytes(8);

    @Option(name = "-threads", usage = "Set the number of threads for servicing sockets (default is Runtime::availableProcessors * 2)")
    private int threads = Runtime.getRuntime().availableProcessors() * 2;

    @Option(name = "-port", usage = "Bind to this port (if sockets > 1, this is the first port). (default will use ephemeral port)")
    private int port = 0;

    @Option(name = "-address", usage = "Bind to this interface (default = ::0)")
    private String address = "::0";

    @Option(name = "-reuse_port", usage = "Reuse socket (must use epoll option).", handler = ExplicitBooleanOptionHandler.class)
    private boolean reusePort = true;

    @Option(name = "-sockets", usage = "Set the number of sockets to use (muse use epoll option).")
    private int sockets = Runtime.getRuntime().availableProcessors() * 2;

    @Option(name = "-engine", usage = "Select channel style (default = NonBlocking).")
    private Engine engine = Engine.NonBlocking;

    @Option(name = "-stats", usage = "Print stats this often (default = 10s).")
    private Duration statsInterval = Duration.seconds(10);

    private final Set<String> threadsUtilized = new HashSet<>();
    private final AtomicLong  messages        = new AtomicLong(0);

    private List<Channel>  channels;
    private EventLoopGroup group;
    private Runnable statsPrinter = new StatsPrinter();

    public static void main(String[] args)
    {
        OptionHandlerRegistry.getRegistry().registerHandler(Size.class, SizeOptionHandler.class);
        OptionHandlerRegistry.getRegistry().registerHandler(Duration.class, DurationHandler.class);
        EchoServer echoServer = new EchoServer();
        CmdLineParser parser = new CmdLineParser(echoServer);
        try
        {
            parser.parseArgument(args);
            echoServer.start();
        }
        catch (CmdLineException e)
        {
            parser.printUsage(System.out);
            System.exit(1);
        }
        catch (Exception e)
        {
            e.printStackTrace(System.out);
            throw Throwables.propagate(e);
        }
    }

    private void start() throws InterruptedException
    {
        Runtime.getRuntime().addShutdownHook(new Thread(statsPrinter));
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(statsPrinter, 0, statsInterval.getQuantity(), statsInterval.getUnit());

        try
        {
            initializeChannels();

            for (Channel channel : channels)
            {
                channel.closeFuture().sync();
            }
        }
        finally
        {
            if (group != null)
            {
                group.shutdownGracefully();
            }
        }
    }

    private void initializeChannels() throws InterruptedException
    {
        Bootstrap b = channelTemplate();
        if (reusePort && engine == Engine.Epoll)
        {
            channels = new ArrayList<>(sockets);
            for (int i = 0; i < sockets; i++)
            {
                Channel channel = b.bind(address, port).await().channel();
                channels.add(channel);
            }
        }
        else
        {
            Channel channel = b.bind(address, port).await().channel();
            channels = Collections.singletonList(channel);
        }
    }

    private Bootstrap channelTemplate()
    {
        Bootstrap b = new Bootstrap()
            .option(ChannelOption.SO_RCVBUF, (int) bufferSize.toBytes())
            .option(ChannelOption.SO_SNDBUF, (int) bufferSize.toBytes())
            .handler(new EchoChannelHandler());
        switch (engine)
        {
            case Blocking:
                group = new OioEventLoopGroup(0);
                b.channel(OioDatagramChannel.class);
                b.group(group);
                break;
            case NonBlocking:
                group = new NioEventLoopGroup(threads);
                b.channel(NioDatagramChannel.class);
                b.group(group);
                break;
            case Epoll:
                group = new EpollEventLoopGroup(threads);
                b.channel(EpollDatagramChannel.class);
                b.group(group);
                if (reusePort)
                {
                    b.option(EpollChannelOption.SO_REUSEPORT, true);
                }
                break;
        }
        return b;
    }

    private class EchoChannelHandler extends ChannelInitializer
    {
        @Override
        protected void initChannel(Channel channel) throws Exception
        {
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter()
            {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
                {
                    DatagramPacket datagram = (DatagramPacket) msg;
                    ctx.write(new DatagramPacket(datagram.content(), datagram.sender()));
                    messages.incrementAndGet();
                    threadsUtilized.add(Thread.currentThread().getName());
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception
                {
                    ctx.flush();
                }
            });
        }
    }

    private class StatsPrinter implements Runnable
    {
        @Override
        public void run()
        {
            System.out.println("Messages = " + messages);
            System.out.println("ThreadsUtilized = " + threadsUtilized);
        }
    }
}
