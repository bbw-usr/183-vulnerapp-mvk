// globals (yay vanilla javascript ftw)
fetchBlogs();
loginCheck();
document.getElementById("login-form")
    .addEventListener("submit", onLoginSubmit);
document.getElementById("logout-form")
    .addEventListener("submit", onLogoutSubmit);
document.getElementById("blog-form")
    .addEventListener("submit", onBlogSubmit);
let devToast = new bootstrap.Toast(
    document.getElementById("devToast"),
    { delay: 10000 }
);

function getCsrfToken() {
  const match = document.cookie
      .split("; ")
      .find((row) => row.startsWith("XSRF-TOKEN="));

  return match ? decodeURIComponent(match.split("=")[1]) : null;
}

async function csrfFetch(url, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const unsafeMethods = ["POST", "PUT", "PATCH", "DELETE"];

  if (unsafeMethods.includes(method)) {
    const token = getCsrfToken();

    if (token) {
      options.headers = {
        ...options.headers,
        "X-XSRF-TOKEN": token,
      };
    }
  }

  return fetch(url, options);
}

function onLoginSubmit(event) {
  const username = event.target[0].value;
  const password = event.target[1].value;
  event.preventDefault();
  csrfFetch("/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({username, password}),
  })
      .then(filterOk)
      .then(response => response.json())
      .then(user => window.sessionStorage.setItem("fullname", user.fullname))
      .then(() => loginCheck());
}

function onLogoutSubmit(event) {
  event.preventDefault();
  window.sessionStorage.removeItem("fullname");
  loginCheck();
}

function onBlogSubmit(event) {
  const data = {"title": event.target[0].value, "body": event.target[1].value};
  event.preventDefault();
  csrfFetch("/api/blog", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
      .then(filterOk)
      .then(() => fetchBlogs())
      .then(() => event.target.reset());
}

// switch display based on login status
function loginCheck() {
  const fullname = window.sessionStorage.getItem("fullname") || "anonymous";
  let authentic = fullname !== "anonymous";
  document.getElementById("login-form").parentElement.hidden = authentic;
  document.getElementById("logout-form").parentElement.hidden = !authentic;
  document.getElementById("username").innerText = fullname;
}

function fetchBlogs() {
  csrfFetch("/api/blog")
      .then(filterOk)
      .then(response => response.json())
      .then(page => renderBlogs(page.content));
}

function renderBlogs(blogs) {
  const blogDiv = document.getElementById("blog-container");
  blogDiv.innerHTML = "" // clear
  for (const blog of blogs) {
    const article = document.createElement("article");

    const title = document.createElement("h2");
    title.textContent = blog.title;

    const date = document.createElement("p");
    date.textContent = blog.createdAt;

    const body = document.createElement("p");
    body.textContent = blog.body;

    article.append(title, date, body);
    blogDiv.appendChild(article);
  }
}

function showDevError(message) {
  document.getElementById("devToastText").textContent = message;
  devToast.show();
}

function filterOk(response) {
  if (response.ok) {
    return response;
  }
  return response.text().then(function(bodyText) {
    let msg = `HTTP ${response.status} ${response.statusText}\n${bodyText}`;
    if(msg.length > 1000){
      msg = msg.substring(0, 1000) + "\n...[truncated]";
    }
    showDevError(msg);
    throw response;
  });
}
