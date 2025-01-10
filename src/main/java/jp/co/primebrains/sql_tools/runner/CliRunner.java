package jp.co.primebrains.sql_tools.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import jp.co.primebrains.sql_tools.command.MainCommand;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@Slf4j
@Component
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    private final MainCommand mainCommand;

    private final IFactory factory;

    public CliRunner(MainCommand mainCommand, IFactory factory) {
        this.mainCommand = mainCommand;
        this.factory = factory;
    }

    @Getter
    private int exitCode;

    @Override
    public void run(String... args) throws Exception {
        exitCode = new CommandLine(mainCommand, factory).execute(args);
    }
}
