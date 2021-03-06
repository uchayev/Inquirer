package Inquirer;

import Inquirer.Util.UtilHash;
import Inquirer.Util.getIP;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

@WebServlet(name = "AuthServlet", urlPatterns = "/AuthServlet") //TODO Проверка продтверждённой почты
public class AuthServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        // Получаем логин/пароль из index.jsp
        String UserName = request.getParameter("UserName");
        String UserPWD = request.getParameter("UserPWD");
        System.out.println("AUTH UserName  --  " + UserName);
        System.out.println("AUTH UserPWD    --  " + UserPWD);


        // логин авторизация и реавторизация, пароль авторизация и реавторизация  /^[a-zA-Z0-9_ +-`'*]+$/gi


        // Хэшируем пароль
        try {
            UserPWD = UtilHash.getHash(UserPWD);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        // Сообщение о результате атентификации
        String message = "";
        Boolean mailbool = false;
        boolean AuthBool = false;
        String JspRedirect = "/index.jsp";
        int user_id = 0;

        // Полученаем IP клиента
        String RemoteIP = getIP.getRemoteIP(request);

        // Подключаем драйвер базы данных.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        // Параметры подключения базы данных
        String dbusername = "postgres";
        String dbpwd = "123";
        String dburl = "jdbc:postgresql://localhost:5432/poll";
        try {
            // Подключаем базу данных
            Connection db = DriverManager.getConnection(dburl, dbusername, dbpwd);
            System.out.println("Auth  -DB-  Connection success!");
            Statement st;
            st = db.createStatement();


            //Проверка на наличие Логина в БД
            String sql = "SELECT user_id FROM users WHERE user_name ILIKE '" + UserName + "' AND user_pwd = '" + UserPWD + "'";
            ResultSet rs = st.executeQuery(sql);
            if (rs.next()) {
                user_id = rs.getInt(1);

                // Генерация ключа сессии
                String symbols = "0123456789ABCDEF";
                StringBuilder session_Key = new StringBuilder();
                int count = 64;
                for (int i = 0; i < count; i++)
                    session_Key.append(symbols.charAt((int) (Math.random() * symbols.length())));

                // Запсиь сесии в БД
                sql = "INSERT INTO user_sessions (user_id, session_key, session_ip) VALUES " +
                        "('" + user_id + "', '" + session_Key + "', '" + RemoteIP + "')";
                st.executeUpdate(sql);

                // Получение уникального идентификатора сессии
                sql = "SELECT session_id FROM user_sessions WHERE session_key='" + session_Key + "' " +
                        "AND user_id='" + user_id + "'";
                rs = st.executeQuery(sql);
                rs.next();
                int session_ID = rs.getInt(1);


                // Запись куки с ключом и индетификатором сессии
                Cookie c_key = new Cookie("SessionKey", session_Key.toString());
                c_key.setMaxAge(72 * 60 * 60);
                response.addCookie(c_key);
                Cookie c_id = new Cookie("SessionID", String.valueOf(session_ID));
                c_id.setMaxAge(72 * 60 * 60);
                response.addCookie(c_id);


                AuthBool = true;


//                // Пулучаем список групп пользоваетля и добавляем его к сообщению
//                message += UserName + " из групп(ы) : <br>\n";
//                sql = "SELECT group_title FROM groups WHERE group_id IN " +
//                        "(SELECT group_id FROM group_entries WHERE user_id='" + user_id + "')";
//                rs = st.executeQuery(sql);
//                while (rs.next()) {
//                    message += rs.getString(1) + "<br>\n";
//                }
//
//
//                // Добавляем к сообщению IP-адрес клиента
//                message += "\n<br>\nclient_ip = " + RemoteIP;


            } else {
                // Сообщение о неудачной аутентификации
                message += "Неверный логин и/или пароль!\n<br>\n";
            }


            if (AuthBool) {
                JspRedirect = "/userpage.jsp";
                sql = "SELECT group_title FROM groups WHERE group_id IN " +
                        "(SELECT group_id FROM group_entries WHERE user_id='" + user_id + "')";
                rs = st.executeQuery(sql);
                while (rs.next()) {
                    if (rs.getString(1).equals("ADMINS"))
                        JspRedirect = "/adminpage.jsp";
                }

                sql = "SELECT group_title FROM groups WHERE group_id IN " +
                        "(SELECT group_id FROM group_entries WHERE user_id='" + user_id + "')";
                rs = st.executeQuery(sql);
                while (rs.next()) {
                    if (rs.getString(1).equals("Mail_OK"))
                        mailbool = true;
                }
                if (!mailbool) {

                    sql = "SELECT user_email FROM users WHERE user_id ='" + user_id + "'";
                    rs = st.executeQuery(sql);
                    rs.next();
                    String UserEmail = rs.getString(1);

                    message += "<br>\n<font color=\"#CC0000\">Ваша учетная запись не подтверждена!</font><br>\n";

                    message += "<center>" +
                            "<form action=\"" + getServletContext().getContextPath() + "/sender\" method=\"post\" name=\"send\">\n" +
                            " <input type=\"hidden\" name=\"UserEmail\" value=\"" + UserEmail + "\">\n" +
                            " <input type=\"hidden\" name=\"JspRedirect\" value=\"" + JspRedirect + "\">\n" +
                            " <input type=\"hidden\" name=\"UserName\" value=\"" + UserName + "\">\n" +
                            "<button type=\"submit\">Повторно отправить письмо<br>\n" +
                            "для подтверждения регистрации</button>\n" +
                            "</form>" +
                            "</center>";
                }

//                message += "<br>\n<br>\n <font color=\"#CC0000\">Регистрация провалилась!</font>\n<br>\n";
//                message = "<b>\n" + message + "\n</b>\n";
                
            }

            // Закрываем соеденение с базой данных
            rs.close();
            st.close();
            db.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Окрываем страницу index.jsp
        // и передаём ей сообщение о результатах аутентифакации.
        message = "\n<b>" + message + "</b>\n";
        request.setAttribute("Message", message);
        request.setAttribute("Nickname", UserName);
        getServletContext().getRequestDispatcher(JspRedirect).forward(
                request, response);

    }
}