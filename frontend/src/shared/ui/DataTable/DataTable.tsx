import "./DataTable.css";

interface Column<T> {
  key: keyof T | string;
  title: string;
  render?: (row: T) => React.ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  emptyLabel?: string;
}

export const DataTable = <T,>({ columns, rows, emptyLabel = "Нет данных" }: DataTableProps<T>) => (
  <div className="ui-table-wrap">
    <table className="ui-table">
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={String(column.key)}>{column.title}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.length > 0 ? (
          rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column) => (
                <td key={String(column.key)}>
                  {column.render
                    ? column.render(row)
                    : String((row as Record<string, unknown>)[String(column.key)] ?? "—")}
                </td>
              ))}
            </tr>
          ))
        ) : (
          <tr>
            <td className="ui-table__empty" colSpan={columns.length}>
              {emptyLabel}
            </td>
          </tr>
        )}
      </tbody>
    </table>
  </div>
);
