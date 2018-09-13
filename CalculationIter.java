package org.egov.pt.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.egov.pt.web.models.CalculationInput;
import org.egov.tracer.model.CustomException;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CalculationIter {



    public static int evaluate(String expression)
    {
        char[] tokens = expression.toCharArray();

        // Stack for numbers: 'values'
        Stack<Integer> values = new Stack<Integer>();

        // Stack for Operators: 'ops'
        Stack<Character> ops = new Stack<Character>();

        for (int i = 0; i < tokens.length; i++)
        {
            // Current token is a whitespace, skip it
            if (tokens[i] == ' ')
                continue;

            // Current token is a number, push it to stack for numbers
            if (tokens[i] >= '0' && tokens[i] <= '9')
            {
                StringBuffer sbuf = new StringBuffer();
                // There may be more than one digits in number
                while (i < tokens.length && tokens[i] >= '0' && tokens[i] <= '9')
                    sbuf.append(tokens[i++]);
                values.push(Integer.parseInt(sbuf.toString()));
            }

            // Current token is an opening brace, push it to 'ops'
            else if (tokens[i] == '(')
                ops.push(tokens[i]);

                // Closing brace encountered, solve entire brace
            else if (tokens[i] == ')')
            {
                while (ops.peek() != '(')
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                ops.pop();
            }

            // Current token is an operator.
            else if (tokens[i] == '+' || tokens[i] == '-' ||
                    tokens[i] == '*' || tokens[i] == '/')
            {
                // While top of 'ops' has same or greater precedence to current
                // token, which is an operator. Apply operator on top of 'ops'
                // to top two elements in values stack
                while (!ops.empty() && hasPrecedence(tokens[i], ops.peek()))
                    values.push(applyOp(ops.pop(), values.pop(), values.pop()));

                // Push current token to 'ops'.
                ops.push(tokens[i]);
            }
        }

        // Entire expression has been parsed at this point, apply remaining
        // ops to remaining values
        while (!ops.empty())
            values.push(applyOp(ops.pop(), values.pop(), values.pop()));

        // Top of 'values' contains result, return it
        return values.pop();
    }

    // Returns true if 'op2' has higher or same precedence as 'op1',
    // otherwise returns false.
    public static boolean hasPrecedence(char op1, char op2)
    {
        if (op2 == '(' || op2 == ')')
            return false;
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-'))
            return false;
        else
            return true;
    }

    // A utility method to apply an operator 'op' on operands 'a'
    // and 'b'. Return the result.
    public static int applyOp(char op, int b, int a)
    {
        switch (op)
        {
            case '+':
                return a + b;
            case '-':
                return a - b;
            case '*':
                return a * b;
            case '/':
                if (b == 0)
                    throw new
                            UnsupportedOperationException("Cannot divide by zero");
                return a / b;
        }
        return 0;
    }

    public enum FunctionEnum {
        FUNCTION1("FUNCTION1"),
        FUNCTION2("FUNCTION2"),
        FUNCTION3("FUNCTION3"),
        FUNCTION4("FUNCTION4"),
        FUNCTION5("FUNCTION5");

        private String value;

        FunctionEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static FunctionEnum fromValue(String text) {
            for (FunctionEnum b : FunctionEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }


    @FunctionalInterface
    interface Function<One, Two, Three, Four, Five> {
        public Five apply(One one, Two two, Three three, Four four);
        //        Function<Integer, Integer, Double> func = (a, b, c) -> a*b+c;
    }

    private Predicate getPredicate(String name){
        Predicate p = null;

        switch (name){
            case "EQUAL":
                 p = o -> {
                    HashMap<String,List<String>> arg = (HashMap<String,List<String>>) o;
                    if(arg.get("slabValue").contains(arg.get("inputValue").get(0)) || arg.get("slabValue").contains("ANY"))
                        return true;
                    else
                        return false;
                };
                break;
            case "BETWEEN":
                 p = o -> {
                    HashMap<String,List<String>> arg = (HashMap<String,List<String>>) o;
                    String[] range = arg.get("slabValue").get(0).split("-");
                    Double from = Double.valueOf(range[0]);
                    Double to = Double.valueOf(range[1]);
                    Double value = Double.valueOf(arg.get("inputValue").get(0));
                    if(value>=from && value<=to)
                        return true;
                    else
                        return false;
                };
                break;
        }

        return p;
    }


   private CalculationInput getCalculationObject(String path){
       ObjectMapper mapper = new ObjectMapper();
       HashMap<String, List<String>> H = new LinkedHashMap<>();
       HashMap<String,Predicate> P = new LinkedHashMap<>();
       try {
           H = mapper.readValue(new File(path), LinkedHashMap.class);
           for(Map.Entry<String, List<String>> entry : H.entrySet() ){
               if(entry.getValue().get(1)!=null)
                  P.put(entry.getKey(),getPredicate(entry.getValue().get(1)));
           }
       } catch (IOException e) {
           e.printStackTrace();
       }
       return new CalculationInput(H,P);
   }



    private List<HashMap<String,List<String>>> getSlab(CalculationInput input){
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, HashMap> S;
        try {
            S = mapper.readValue(new File("/home/aniket/Documents/egovGit/egov-services/rainmaker/pt-services-v2/src/test/resources/trialSlabs.json"), LinkedHashMap.class);
        }
        catch (Exception e)
        {throw  new CustomException();
        }
        HashMap<String, List<String>> H = input.getH();
        List<HashMap<String,List<String>>> slabs = (List<HashMap<String,List<String>>>) S.get(H.get("jsonName").get(0));
        List<HashMap<String,List<String>>> validSlabs = getValidSlabs(slabs,input);

        return validSlabs;
    }


    private List<HashMap<String,List<String>>> getValidSlabs(List<HashMap<String,List<String>>> slabs,CalculationInput input){
        HashMap<String, List<String>> H = input.getH();
        HashMap<String,Predicate> P = input.getP();
        List<HashMap<String,List<String>>> validSlabs = new LinkedList<>();

        slabs.forEach(slab ->{
            if(validateSlab(slab,input))
                validSlabs.add(slab);
        });
        return validSlabs;
    }


    private Boolean validateSlab(HashMap<String,List<String>> slab,CalculationInput input){
        HashMap<String, List<String>> H = input.getH();
        HashMap<String,Predicate> P = input.getP();
        String key;List<String> values;
        Predicate p;
        HashMap<String,List<String>> arg = new HashMap();

        for (Map.Entry<String, List<String>> entry  : H.entrySet()){
            key = entry.getKey();
            values = entry.getValue();
            if(P.containsKey(key)){
                p = P.get(key);
                arg.clear();
                arg.put("inputValue",Collections.singletonList(values.get(0)));
                arg.put("slabValue",slab.get(key));
                if(!p.test(arg))
                    return false;
            }
        }
        return true;
    }


    double calculateCharge(CalculationInput input,List<HashMap<String,List<String>>> slabs){
        List<Double> total = new LinkedList<>();
        Pattern pattern = Pattern.compile("\\$([a-zA-Z0-9\\[\\]]*?)\\$");
        slabs.forEach(slab -> {
            String formulla = input.getH().get("formulla").get(0);
            Matcher matcher = pattern.matcher(formulla);
            int start = 0;
            List<String> parameters = new LinkedList<>();
            while (matcher.find(start)) {
                start = matcher.start()+1;
                parameters.add(matcher.group(1));
            }
            for(String param:parameters){
                if(param.contains("[S]"))
                    formulla = formulla.replace("$"+param+"$",slab.get(param.substring(0,param.indexOf("["))).get(0));
                else if(param.contains("[I]"))
                    formulla = formulla.replace("$"+param+"$",input.getH().get(param.substring(0,param.indexOf("["))).get(0));
            };
            System.out.println("formaulla: "+formulla);
            total.add(((double)evaluate(formulla)));
        });
        return total.stream().mapToDouble(Double::doubleValue).sum();
    }


    @Test
    public void name() {

        CalculationInput input = getCalculationObject("/home/aniket/Documents/egovGit/egov-services/rainmaker/pt-services-v2/src/test/input.json");
        List<HashMap<String,List<String>>> slabs = getSlab(input);
        double tax = calculateCharge(input,slabs);
        System.out.println(tax);

    }
}
