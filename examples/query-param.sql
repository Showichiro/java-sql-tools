-- Example 1: Simple parameterized query
SELECT * FROM EMPLOYEES WHERE DEPARTMENT = :dept;

-- Example 2: Parameterized query with multiple conditions
SELECT NAME, SALARY 
FROM EMPLOYEES 
WHERE DEPARTMENT = :dept AND SALARY > :min_salary;

-- Example 3: Date range parameterized query
SELECT * 
FROM EMPLOYEES 
WHERE HIRE_DATE BETWEEN :start_date AND :end_date;

-- Example 4: Parameterized query with ordering
SELECT NAME, DEPARTMENT, SALARY
FROM EMPLOYEES
WHERE AGE > :min_age
ORDER BY SALARY DESC;
