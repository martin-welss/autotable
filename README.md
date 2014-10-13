autotable
=========

autotable tries to be as smart as possible and do the right things automatically:
* it reads a csv file into PostgreSQL or MySQL
* creates a table with the same name as the file (just the basename, without path and suffix)
* tries to guess the datatypes of the columns from the first 100 lines
* inserts the data
* it recognizes \(multiline\) strings, integers, floating point numbers, dates, timestamps and json data

The first line of the file is expected to contain the column names and the standard column separator is ; (semicolon)
* empty lines are ignored
* empty columns are null
* any field can optionally be enclosed in double quotes ("), those are stripped
* a text field enclosed in double quotes can contain semicolons, no escaping necessary
* the first column becomes the primary key
* autotable takes a sample of the first 100 lines to guess the datatypes, which are: text, bigint, numeric, timestamp and json

NOTICE: MySQL has no native JSON datatype, so JSON is imported into MySQL as text column. But the mysql text type does not handle well JSON newlines in string literals and other escapes. You have been warned...

Run
---

The script reads the database login from the file $HOME/autotable.properties, which should contain at least three entries like this:
  
    jdbc_url: jdbc:postgresql:example
    jdbc_user: wildfly
    jdbc_password: wildfly

To run the script you need to have Java and Groovy installed and the JDBC-Driver of your choice at hand. So here's the commandline to import table _books_ into postgres:

    groovy -cp ../lib/postgresql-9.3-1101.jdbc41.jar autotable.groovy ../stuff/books.csv

Options
-------

In autotable.properties you can set several options. For example to add data to an existing table, just set **drop_create_table** to false. Here is the complete list with their default values: 
  
    drop_create_table: true
    guess_lines: 100
    date_formats: [dd.MM.yyyy HH:mm:ss, dd.MM.yyyy HH:mm, dd.MM.yyyy]
    sql_date: yyyy-MM-dd HH:mm:ss
    pg_string_escape: true
    string_delimiter: (^\"|\"$)
    field_separator: ;(?=([^\"]*\"[^\"]*\")*[^\"]*$)
    json_type: json
    
  
  
