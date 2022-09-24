package io.spring.batch.helloworld.batch;

import io.spring.batch.helloworld.domain.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.jms.JmsItemReader;
import org.springframework.batch.item.jms.JmsItemWriter;
import org.springframework.batch.item.jms.builder.JmsItemReaderBuilder;
import org.springframework.batch.item.jms.builder.JmsItemWriterBuilder;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.batch.item.xml.builder.StaxEventItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.oxm.xstream.XStreamMarshaller;

import javax.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class JobConfiguration {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(connectionFactory);
        cachingConnectionFactory.afterPropertiesSet();

        JmsTemplate jmsTemplate = new JmsTemplate(cachingConnectionFactory);
        jmsTemplate.setDefaultDestinationName("customers");
        jmsTemplate.setReceiveTimeout(5000L);

        return jmsTemplate;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Customer> customerFileReader(
            @Value("#{jobParameters['customerFile']}") Resource inputFile) {

        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerFileReader")
                .resource(inputFile)
                .delimited()
                .names("firstName",
                        "middleInitial",
                        "lastName",
                        "address",
                        "city",
                        "state",
                        "zip")
                .targetType(Customer.class)
                .build();
    }

    @Bean
    @StepScope
    public StaxEventItemWriter<Customer> xmlOutputWriter(
            @Value("#{jobParameters['outputFile']}") Resource outputFile) {

        Map<String, Class> aliases = new HashMap<>();
        aliases.put("customer", Customer.class);

        XStreamMarshaller marshaller = new XStreamMarshaller();
        marshaller.setAliases(aliases);

        return new StaxEventItemWriterBuilder<Customer>()
                .name("xmlOutputWriter")
                .resource(outputFile)
                .marshaller(marshaller)
                .rootTagName("customers")
                .build();
    }

    @Bean
    public JmsItemReader<Customer> jmsItemReader(JmsTemplate jmsTemplate) {

        return new JmsItemReaderBuilder<Customer>()
                .jmsTemplate(jmsTemplate)
                .itemType(Customer.class)
                .build();
    }

    @Bean
    public JmsItemWriter<Customer> jmsItemWriter(JmsTemplate jmsTemplate) {

        return new JmsItemWriterBuilder<Customer>()
                .jmsTemplate(jmsTemplate)
                .build();
    }

    @Bean
    public Step formatInputStep() throws Exception {
        return stepBuilderFactory.get("formatInputStep")
                .<Customer, Customer>chunk(10)
                .reader(customerFileReader(null))
                .writer(jmsItemWriter(null))
                .build();
    }

    @Bean
    public Step formatOutputStep() throws Exception {
        return stepBuilderFactory.get("formatOutputStep")
                .<Customer, Customer>chunk(10)
                .reader(jmsItemReader(null))
                .writer(xmlOutputWriter(null))
                .build();
    }

    @Bean
    public Job jmsFormatJob() throws Exception {
        return jobBuilderFactory.get("jmsFormatJob")
                .start(formatInputStep())
                .next(formatOutputStep())
                .incrementer(new RunIdIncrementer())
                .build();
    }
}
