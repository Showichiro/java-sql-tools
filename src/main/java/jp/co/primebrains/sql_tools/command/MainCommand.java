package jp.co.primebrains.sql_tools.command;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import jp.co.primebrains.sql_tools.command.subcommands.QueryCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "sql_tools", mixinStandardHelpOptions = true, subcommands = { QueryCommand.class })
public class MainCommand implements Callable<Integer> {

    @Option(names = { "-h", "--help" }, description = "show this help", usageHelp = true)
    boolean showHelp;

    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
