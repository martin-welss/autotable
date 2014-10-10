#!/usr/bin/env groovy
import groovy.transform.*
import groovy.sql.Sql

import java.nio.file.*



sysprops=new File("${System.properties.'user.home'}/autotable.properties")
if(sysprops.exists()==false || args.size()==0) {
	help()
	System.exit(1)
}
println("reading properties from $sysprops")
sysprops.withInputStream {
	System.properties.load(it)
}


// class for options to make them accessible by class ATColumn
class ATOptions {
	static jdbc_url=System.properties.'jdbc_url'
	static jdbc_user=System.properties.'jdbc_user'
	static jdbc_password=System.properties.'jdbc_password'
	static guess_lines=Integer.getInteger("guess_lines", 100)
	static drop_create_table=System.properties.'drop_create_table'==null ? true : Boolean.getBoolean("drop_create_table")
	static date_formats=["dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy"]
	static sql_date=System.properties.'sql_date' ?: "yyyy-MM-dd HH:mm:ss"
	static string_delimiter=/(^\"|\"$)/
    static json_type="json"
	
	// enable C-Style escapes like /n
	static pg_string_escape=System.properties.'pg_string_escape'==null ? true : Boolean.getBoolean("pg_string_escape") 
	
	// ignore ; in quotes
	static field_separator=/;(?=([^\"]*\"[^\"]*\")*[^\"]*$)/    
}

@Canonical
class ATColumn {	
	String name
	String type // bigint, numeric, text, timestamp
	boolean primary=false
	static String BIGINT="bigint"
	static String NUMERIC="numeric"
	static String TEXT="text"
	static String TIMESTAMP="timestamp"
	static String JSON=ATOptions.json_type
	
	static void setMysqlTypes() {
		NUMERIC="double"
		JSON="text"
		ATOptions.pg_string_escape=false
	}
	
	def addTypeSample(String value) {
		value=value.trim().replaceAll(ATOptions.string_delimiter, "")
		if(value.size()==0) { return }
		def sample=TEXT
		if(value.isNumber()) { sample=NUMERIC }
		if(value.isLong()) { sample=BIGINT }
		if(sample==TEXT && value =~ /^(\[|\{)/) { sample=JSON }
		if(sample==TEXT) {
			ATOptions.date_formats.each {
				try {
					Date.parse(it,value)
					sample=TIMESTAMP
				}
				catch(Exception x) { }
			}
		}
		//println "type: $type - sample: $sample - value: $value"
		
		if(type==null) { type=sample; return }
		if(type==sample) { return }
		if(type==NUMERIC && sample==BIGINT) { return }
		if(type==BIGINT && sample==NUMERIC) { type=NUMERIC; return }
		type=TEXT //type!=sample -> text
	}
	
	def getDDL() {
		return name+" "+type+(primary ? " primary key" : "")
	}
	
	def getSQLValue(String field) {
		if(field.length()==0) { return "null" }
		field=field.trim().replaceAll(ATOptions.string_delimiter, "")
		if(type==TEXT) {return ATOptions.pg_string_escape ? "E'"+field+"'" : "'"+field+"'" }
		if(type==JSON) {return "'"+field+"'" }
		if(type==TIMESTAMP) {
			for(def format: ATOptions.date_formats) {
				try {
					def time=Date.parse(format,field)
					return "'"+time.format(ATOptions.sql_date)+"'"
				}
				catch(Exception x) { }
			}
			return "null"
		}
		return field
	}
}


tablename=Paths.get(args[0]).getFileName().toString().tokenize('.')[0]
columns=[]

println "connecting to database: ${ATOptions.jdbc_url} as user ${ATOptions.jdbc_user}"
def sql= Sql.newInstance("${ATOptions.jdbc_url}",
					"${ATOptions.jdbc_user}",
					"${ATOptions.jdbc_password}")


if((ATOptions.jdbc_url).toLowerCase().indexOf("mysql")>4) {
	ATColumn.setMysqlTypes()
}

data=new File(args[0])

// We analyze the first x lines to guess columntypes
line=0;
data.withReader { reader ->
	while((row=reader.readLine())!=null) {
		line++
		row=row.trim()
		if(row.length()==0) { continue }
		if(line>ATOptions.guess_lines) { break }
		def fields=row.split(ATOptions.field_separator)
		if(line==1) {
			fields.each { columns << new ATColumn(it,null) }
			columns[0].primary=true
			continue
		}
		[fields, columns].transpose().each { field,column -> column.addTypeSample(field) }
	}
}

if(ATOptions.drop_create_table) {
	println "drop table $tablename"
	sql.execute("drop table if exists "+tablename)
	command="create table "+tablename
	command+=" (" + columns*.getDDL().join(",") + ")"
	println command
	sql.execute(command)	
}


// read data
line=0
inserts=0
data.splitEachLine(ATOptions.field_separator) { fields ->
	line++
	if(line>1 && fields.size()>1) {
		command="insert into "+tablename
		command+=" (" + columns*.name.join(",") + ") values "
		vallist=[]
		[fields, columns].transpose().each { field,column -> vallist << column.getSQLValue(field) }
		command+=" (" + vallist.join(",") + ")"
		//println command
		sql.execute(command)
		inserts++
	}
}
println "== total lines: $line == inserts: $inserts =="
sql.close()


void help() {
	println "A properties file \$HOME/autotable.properties is mandatory which contains db connection data and options"
	println "Here is a list of the properties and thier default values. The three jdbc properties must be set, all others are optional\n"
	println "jdbc_url: "
	println "jdbc_user: "
	println "jdbc_password: \n"
	println "drop_create_table: $ATOptions.drop_create_table"
	println "guess_lines: $ATOptions.guess_lines"
	println "date_formats: $ATOptions.date_formats"
	println "sql_date: $ATOptions.sql_date"
	println "pg_string_escape: $ATOptions.pg_string_escape"
	println "string_delimiter: $ATOptions.string_delimiter"
	println "field_separator: $ATOptions.field_separator"
	println "json_type: $ATOptions.json_type"
}
