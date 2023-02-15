package br.com.maurigvs.config;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.maurigvs.listener.JobCompletionNotificationListener;
import br.com.maurigvs.model.Person;
import br.com.maurigvs.model.PersonItemProcessor;

@Configuration
public class BatchConfiguration {

    /**
     * @return an ItemReader. It looks for a file called sample-data.csv and parses each
     * line item with enough information to turn it into a Person.
     */
    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data.csv"))
                .delimited()
                .names("firstName", "lastName")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();
    }

    /**
     * @return an instance of the PersonItemProcessor that you defined earlier, meant
     * to convert the data to upper case.
     */
    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    /**
     * @param dataSource
     * @return an ItemWriter. This one is aimed at a JDBC destination and automatically
     * gets a copy of the dataSource created by @EnableBatchProcessing. It includes the
     * SQL statement needed to insert a single Person, driven by Java bean properties.
     */
    @Bean
    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    /**
     * The first method defines the job, and the second one defines a single step. Jobs are
     * built from steps, where each step can involve a reader, a processor, and a writer.
     *
     * In this job definition, you need an incrementer, because jobs use a database to maintain
     * execution state. You then list each step, (though this job has only one step). The job
     * ends, and the Java API produces a perfectly configured job.
     *
     * In the step definition, you define how much data to write at a time. In this case,
     * it writes up to ten records at a time. Next, you configure the reader, processor,
     * and writer by using the beans injected earlier.
     */

    /**
     * defines the job
     *
     * @param jobRepository
     * @param listener
     * @param step1
     * @return
     */
    @Bean
    public Job importUserJob(JobRepository jobRepository, JobCompletionNotificationListener listener, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .end()
                .build();
    }

    /**
     * defines a single step for the job
     * @param jobRepository
     * @param transactionManager
     * @param writer
     * @return
     */
    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager, JdbcBatchItemWriter<Person> writer) {
        return new StepBuilder("step1", jobRepository)
                .<Person, Person> chunk(10, transactionManager)
                .reader(reader())
                .processor(processor())
                .writer(writer)
                .build();
    }
}
