package com.codeminders.hamake.params;

import com.codeminders.hamake.Utils;
import com.codeminders.hamake.context.Context;

public class Literal extends ParameterItem{
	private String value;
	
	public Literal(String value){
		this.value = value;
	}
	
	public String getValue(Context context){
		return Utils.replaceVariables(context, value); 
	}
	
}
