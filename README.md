autotable
=========

autotable tries to be as smart as possible and do the right things automatically:
* it reads a csv file into PostgreSQL or MySQL
* creates a table with the same name as the file
* tries to guess the datatypes of the columns
* and inserts the data

The first line of the file is expected to contain the column names and the standard column separator is ;
* empty lines are ignored
* empty columns are null
* any field can be enclosed in double quotes ("), those are stripped
* the first column becomes the primary key
* autotable takes a sample of the first 100 lines to guess the datatypes, which are: text, bigint, numeric and timestamp

Run
---

The script reads the database login from the file $HOME/system.properties, which should contain three entries like this:
  
    autotable.jdbc.url: jdbc:postgresql:example
    autotable.user: wildfly
    autotable.password: wildfly

To run the script you need to have Groovy installed and the JDBC-Driver of your choice at hand. So here's the commandline to import table _books_ into postgres:

    groovy -cp ../lib/postgresql-9.3-1101.jdbc41.jar autotable.groovy ../stuff/books.csv

Options
-------

At the beginning of the script you can set some options. For example to add data to an existing table, just set DROP\_CREATE\_TABLE to false. 
  
    GUESS_LINES=100
    DROP_CREATE_TABLE=true
    CREATE_TABLE=true
    STRING_DELIMITER=/\"/
    FIELD_SEPARATOR=/;(?=([^\"]*\"[^\"]*\")*[^\"]*$)/    // ignore ; in quotes
    PROPERTIES="system.properties"
    DATE_FORMATS=["dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy"]
    SQL_DATE="yyyy-MM-dd HH:mm:ss"
  
  
