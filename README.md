autotable
=========

autotable does the following:
* it reads a csv file into PostgreSQL or MySQL
* creates a table with the same name as the file
* tries to guess the datatypes of the columns
* and inserts the data. 

The first line of the file is expected to contain the column names and the standard column separator is ;
Empty lines are ignored
Empty columns are null
The first column becomes the primary key
autotable takes a sample of the first 100 lines to guess the datatypes, which are: text, bigint, numeric and timestamp

The script reads the database login from the file $HOME/system.properties, which should contain these three entries:
  
    autotable.jdbc.url: jdbc:postgresql:example
    autotable.user: wildfly
    autotable.password: wildfly

To run the script you need to have Groovy installed and the JDBC-Driver of your choice at hand. So here's the commandline to import table books into postgres:

    groovy -cp ../lib/postgresql-9.3-1101.jdbc41.jar autotable.groovy ../stuff/books.csv

Options
-------

At the beginning of the file you can set some options. For example to add data to an existing table, just set DROP\_TABLE and CREATE\_TABLE to false. 
  
    GUESS_LINES=100
    DROP_TABLE=true
    CREATE_TABLE=true
    STRING_DELIMITER=/\"/
    FIELD_SEPARATOR=/;(?=([^\"]*\"[^\"]*\")*[^\"]*$)/    // ignore ; in quotes
    PROPERTIES="system.properties"
    DATE_FORMATS=["dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy"]
    SQL_DATE="yyyy-MM-dd HH:mm:ss"
  
  
